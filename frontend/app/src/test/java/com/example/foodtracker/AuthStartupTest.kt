package com.example.foodtracker

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.foodtracker.api.ProfileApi
import com.example.foodtracker.data.ProfileSnapshotStore
import com.example.foodtracker.model.GoalsDto
import com.example.foodtracker.model.ProfileDto
import com.example.foodtracker.model.TdeeResponseDto
import com.example.foodtracker.viewmodel.AuthUiState
import com.example.foodtracker.viewmodel.AuthUser
import com.example.foodtracker.viewmodel.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthStartupTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val profile = ProfileDto("Ana", 30, "FEMALE", 165, 60f, 58f, "MODERATE")

    private class FakeApi(var getProfile: () -> Response<ProfileDto>) : ProfileApi {
        override suspend fun getProfile() = getProfile.invoke()
        override suspend fun saveProfile(profile: ProfileDto): Response<ProfileDto> = throw UnsupportedOperationException()
        override suspend fun calculateTdee(profile: ProfileDto): Response<TdeeResponseDto> = throw UnsupportedOperationException()
        override suspend fun getGoals(): Response<GoalsDto> = throw UnsupportedOperationException()
        override suspend fun deleteAccount(): Response<Unit> = throw UnsupportedOperationException()
    }

    private val dsScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private fun store() = ProfileSnapshotStore(
        PreferenceDataStoreFactory.create(
            scope = dsScope,
            produceFile = { File(tempFolder.newFolder(), "profile_snapshot.preferences_pb") }
        )
    )

    private fun error(code: Int) = Response.error<ProfileDto>(code, "".toResponseBody())

    private fun vm(api: ProfileApi, store: ProfileSnapshotStore, uid: String = "u1") = AuthViewModel(
        snapshots = store,
        profileApi = api,
        currentUser = { AuthUser(uid, true) },
    )

    /** DataStore I/O runs on a real dispatcher, so wait (in real time) for the state to settle. */
    private suspend fun AuthViewModel.start() {
        checkAuthState()
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(5000) {
                uiState.first { it !is AuthUiState.Loading && it !is AuthUiState.Idle }
            }
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() { dsScope.cancel(); Dispatchers.resetMain() }

    @Test
    fun offlineStartupWithSnapshotGoesHome() = runTest {
        val s = store()
        s.save("u1", profile, null)
        val vm = vm(FakeApi { throw IOException("offline") }, s)
        vm.start()
        assertEquals(AuthUiState.NavigateHome, vm.uiState.value)
        assertEquals("Ana", vm.loadedProfile.value?.name)
    }

    @Test
    fun offlineStartupWithoutSnapshotShowsOffline() = runTest {
        val vm = vm(FakeApi { throw IOException("offline") }, store())
        vm.start()
        assertEquals(AuthUiState.Offline, vm.uiState.value)
    }

    @Test
    fun notFoundGoesToOnboarding() = runTest {
        val s = store()
        s.save("u1", profile, null)
        val vm = vm(FakeApi { error(404) }, s)
        vm.start()
        assertEquals(AuthUiState.NavigateOnboarding, vm.uiState.value)
    }

    @Test
    fun serverErrorUsesSnapshot() = runTest {
        val s = store()
        s.save("u1", profile, null)
        val vm = vm(FakeApi { error(503) }, s)
        vm.start()
        assertEquals(AuthUiState.NavigateHome, vm.uiState.value)
    }

    @Test
    fun otherCodesShowOffline() = runTest {
        val vm = vm(FakeApi { error(400) }, store())
        vm.start()
        assertEquals(AuthUiState.Offline, vm.uiState.value)
    }

    @Test
    fun unauthorizedProfileGoesToLogin() = runTest {
        for (code in listOf(401, 403)) {
            val s = store()
            s.save("u1", profile, null)
            var signedOut = false
            val vm = AuthViewModel(
                snapshots = s, profileApi = FakeApi { error(code) },
                currentUser = { AuthUser("u1", true) }, signOut = { signedOut = true },
            )
            vm.start()
            assertEquals(AuthUiState.NavigateLogin, vm.uiState.value)
            assertTrue(signedOut)
            assertNull(vm.loadedProfile.value)
        }
    }

    @Test
    fun ownerMismatchOnLoginClearsJournalBeforeHome() = runTest {
        val j = TestJournal()
        j.uid = "someone-else"
        j.repo.setHydration(java.time.LocalDate.of(2026, 11, 4), 1.5f)
        val homeStates = mutableListOf<Int>()
        val vm = AuthViewModel(
            snapshots = store(), profileApi = FakeApi { Response.success(profile) },
            currentUser = { AuthUser("u1", true) },
            bindJournal = { uid -> j.repo.resetIfOwnedByOther(uid) },
            onHome = { homeStates += j.repo.pendingCount.first() },
        )
        vm.start()
        assertEquals(AuthUiState.NavigateHome, vm.uiState.value)
        assertEquals(0, j.repo.pendingCount.first())
        assertNull(j.repo.ownerUid())
        assertEquals(listOf(0), homeStates)
    }

    @Test
    fun sameOwnerKeepsJournalOnLogin() = runTest {
        val j = TestJournal()
        j.uid = "u1"
        j.repo.setHydration(java.time.LocalDate.of(2026, 11, 4), 1.5f)
        val s = store()
        s.save("u1", profile, null)
        var bound: String? = null
        val vm = AuthViewModel(
            snapshots = s, profileApi = FakeApi { throw IOException("offline") },
            currentUser = { AuthUser("u1", true) },
            bindJournal = { uid -> bound = uid; j.repo.resetIfOwnedByOther(uid) },
        )
        vm.start()
        assertEquals(AuthUiState.NavigateHome, vm.uiState.value)
        assertEquals("u1", bound)
        assertEquals(1, j.repo.pendingCount.first())
    }

    @Test
    fun snapshotForOtherUidIsIgnored() = runTest {
        val s = store()
        s.save("someone-else", profile, null)
        val vm = vm(FakeApi { throw IOException("offline") }, s)
        vm.start()
        assertEquals(AuthUiState.Offline, vm.uiState.value)
    }

    @Test
    fun successSavesSnapshot() = runTest {
        val s = store()
        val vm = vm(FakeApi { Response.success(profile) }, s)
        vm.start()
        assertEquals(AuthUiState.NavigateHome, vm.uiState.value)
        assertNotNull(s.read("u1")?.profile)
    }

    @Test
    fun nonIoFailureUsesSnapshot() = runTest {
        val s = store()
        s.save("u1", profile, null)
        val vm = vm(FakeApi { throw IllegalStateException("token refresh failed") }, s)
        vm.start()
        assertEquals(AuthUiState.NavigateHome, vm.uiState.value)
    }

    @Test
    fun snapshotWriteFailureStillGoesHome() = runTest {
        val failing = ProfileSnapshotStore(
            PreferenceDataStoreFactory.create(
                scope = dsScope,
                produceFile = { File(tempFolder.newFolder(), "x.preferences_pb") }
            )
        )
        dsScope.cancel() // writes now fail
        val vm = vm(FakeApi { Response.success(profile) }, failing)
        vm.start()
        assertEquals(AuthUiState.NavigateHome, vm.uiState.value)
    }
}
