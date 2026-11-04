package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.ProfileApi
import com.example.foodtracker.data.ProfileSnapshotStore
import com.example.foodtracker.api.RetrofitInstance
import com.example.foodtracker.model.ActivityLevel
import com.example.foodtracker.model.Gender
import com.example.foodtracker.model.ProfileDto
import com.example.foodtracker.model.UserProfile
import com.example.foodtracker.model.toProfileDto
import com.example.foodtracker.model.toUserProfile
import com.example.foodtracker.util.mapFirebaseError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class ProfileSaveState {
    object Idle : ProfileSaveState()
    object Saving : ProfileSaveState()
    object Success : ProfileSaveState()
    data class Error(@StringRes val messageRes: Int) : ProfileSaveState()
}

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Error(@StringRes val messageRes: Int) : AuthUiState()
    object NavigateLogin : AuthUiState()
    object NavigateHome : AuthUiState()
    object NavigateOnboarding : AuthUiState()
    object NavigateVerifyEmail : AuthUiState()
    /** Startup could not reach the server and there is no cached profile to fall back on. */
    object Offline : AuthUiState()
}

/** The bits of the signed-in Firebase user the startup flow needs (seam for tests). */
data class AuthUser(val uid: String, val isEmailVerified: Boolean)

sealed class PasswordResetState {
    object Idle : PasswordResetState()
    object Sending : PasswordResetState()
    object Sent : PasswordResetState()
    data class Error(@StringRes val messageRes: Int) : PasswordResetState()
}

sealed class DeleteAccountState {
    object Idle : DeleteAccountState()
    object Deleting : DeleteAccountState()
    data class Error(@StringRes val messageRes: Int) : DeleteAccountState()
}

data class VerifyEmailUiState(
    val isChecking: Boolean = false,
    val isResending: Boolean = false,
    val resendCooldownSec: Int = 0,
    val notYetVerified: Boolean = false
)

data class OnboardingData(
    val name: String = "",
    val age: String = "",
    val gender: Gender = Gender.MALE,
    val heightCm: String = "",
    val currentWeightKg: String = "",
    val targetWeightKg: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val takesCreatine: Boolean = false,
    val calculatedCalorieGoal: Int? = null,
    val isTdeeLoading: Boolean = false
)

/** Parses the onboarding form into a profile; null when age/height/current weight are invalid. */
internal fun OnboardingData.toProfileDtoOrNull(): ProfileDto? {
    val age = age.toIntOrNull() ?: return null
    val heightCm = heightCm.toIntOrNull() ?: return null
    val currentKg = currentWeightKg.replace(",", ".").toFloatOrNull() ?: return null
    val targetKg = targetWeightKg.replace(",", ".").toFloatOrNull() ?: currentKg
    return ProfileDto(
        name, age, gender.name, heightCm, currentKg, targetKg, activityLevel.name,
        takesCreatine = takesCreatine, hydrationGoalOverrideL = null
    )
}

class AuthViewModel(
    private val snapshots: ProfileSnapshotStore? = null,
    private val profileApi: ProfileApi = RetrofitInstance.retrofit.create(ProfileApi::class.java),
    private val currentUser: () -> AuthUser? = {
        FirebaseAuth.getInstance().currentUser?.let { AuthUser(it.uid, it.isEmailVerified) }
    },
) : ViewModel() {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _onboardingStep = MutableStateFlow(0)
    val onboardingStep: StateFlow<Int> = _onboardingStep.asStateFlow()

    private val _onboardingData = MutableStateFlow(OnboardingData())
    val onboardingData: StateFlow<OnboardingData> = _onboardingData.asStateFlow()

    private val _loadedProfile = MutableStateFlow<UserProfile?>(null)
    val loadedProfile: StateFlow<UserProfile?> = _loadedProfile.asStateFlow()

    private val _profileUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val profileUpdates: SharedFlow<Unit> = _profileUpdates.asSharedFlow()

    private val _profileSaveState = MutableStateFlow<ProfileSaveState>(ProfileSaveState.Idle)
    val profileSaveState: StateFlow<ProfileSaveState> = _profileSaveState.asStateFlow()

    fun clearProfileSaveState() { _profileSaveState.value = ProfileSaveState.Idle }

    private val _passwordResetState = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState.asStateFlow()
    fun clearPasswordResetState() { _passwordResetState.value = PasswordResetState.Idle }

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()
    fun clearDeleteAccountState() { _deleteAccountState.value = DeleteAccountState.Idle }

    /**
     * Deletes the account: backend DELETE /profile (cascades DB data + deletes the
     * Firebase Auth user via Admin SDK), then signs out locally and invokes [onDeleted]
     * to navigate. On failure the state holds an error so the dialog can show it inline.
     */
    fun deleteAccount(onDeleted: () -> Unit) {
        if (_deleteAccountState.value == DeleteAccountState.Deleting) return
        _deleteAccountState.value = DeleteAccountState.Deleting
        viewModelScope.launch {
            try {
                val response = profileApi.deleteAccount()
                if (response.isSuccessful) {
                    auth.signOut()
                    // Reset uiState so LoginScreen's LaunchedEffect(uiState) doesn't bounce
                    // back to Home on the stale NavigateHome value (same as logout()).
                    _uiState.value = AuthUiState.NavigateLogin
                    _onboardingData.value = OnboardingData()
                    _onboardingStep.value = 0
                    _loadedProfile.value = null
                    _deleteAccountState.value = DeleteAccountState.Idle
                    onDeleted()
                } else {
                    _deleteAccountState.value =
                        DeleteAccountState.Error(R.string.error_delete_account)
                }
            } catch (e: java.io.IOException) {
                _deleteAccountState.value =
                    DeleteAccountState.Error(R.string.error_no_internet)
            } catch (e: Exception) {
                _deleteAccountState.value =
                    DeleteAccountState.Error(R.string.error_delete_account)
            }
        }
    }

    private val _verifyEmailState = MutableStateFlow(VerifyEmailUiState())
    val verifyEmailState: StateFlow<VerifyEmailUiState> = _verifyEmailState.asStateFlow()
    fun resetVerifyEmailState() { _verifyEmailState.value = VerifyEmailUiState() }

    private val _resetEmailPrefill = MutableStateFlow("")
    val resetEmailPrefill: StateFlow<String> = _resetEmailPrefill.asStateFlow()
    fun setResetEmailPrefill(email: String) { _resetEmailPrefill.value = email }

    val currentUserEmail: String? get() = auth.currentUser?.email

    private var cooldownJob: Job? = null
    private fun startResendCooldown(seconds: Int = 60) {
        cooldownJob?.cancel()
        _verifyEmailState.value = _verifyEmailState.value.copy(resendCooldownSec = seconds)
        cooldownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _verifyEmailState.value = _verifyEmailState.value.copy(resendCooldownSec = remaining)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _passwordResetState.value = PasswordResetState.Sending
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                _passwordResetState.value = PasswordResetState.Sent
            } catch (e: Exception) {
                _passwordResetState.value = PasswordResetState.Error(mapFirebaseError(e))
            }
        }
    }

    fun resendVerificationEmail() {
        val s = _verifyEmailState.value
        if (s.resendCooldownSec > 0 || s.isResending) return
        viewModelScope.launch {
            _verifyEmailState.value = _verifyEmailState.value.copy(isResending = true)
            try {
                auth.currentUser?.sendEmailVerification()?.await()
                startResendCooldown(60)
            } catch (_: Exception) {
                // userul poate reîncerca
            } finally {
                _verifyEmailState.value = _verifyEmailState.value.copy(isResending = false)
            }
        }
    }

    fun checkEmailVerified() {
        viewModelScope.launch {
            _verifyEmailState.value = _verifyEmailState.value.copy(isChecking = true, notYetVerified = false)
            try {
                auth.currentUser?.reload()?.await()
                if (auth.currentUser?.isEmailVerified == true) {
                    resolvePostAuth()
                } else {
                    _verifyEmailState.value = _verifyEmailState.value.copy(notYetVerified = true)
                }
            } catch (_: Exception) {
                _verifyEmailState.value = _verifyEmailState.value.copy(notYetVerified = true)
            } finally {
                _verifyEmailState.value = _verifyEmailState.value.copy(isChecking = false)
            }
        }
    }

    /** Called by SplashScreen on startup. */
    fun checkAuthState() {
        viewModelScope.launch {
            val user = currentUser()
            if (user == null) {
                _uiState.value = AuthUiState.NavigateLogin
                return@launch
            }
            if (!user.isEmailVerified) {
                _uiState.value = AuthUiState.NavigateVerifyEmail
                return@launch
            }
            _uiState.value = AuthUiState.Loading
            _uiState.value = fetchProfileState(user.uid)
        }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                if (auth.currentUser?.isEmailVerified == false) {
                    _uiState.value = AuthUiState.NavigateVerifyEmail
                } else {
                    resolvePostAuth()
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
            }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
                return@launch
            }
            // Contul e creat; emailul de verificare e best-effort (resend din ecran).
            try {
                auth.currentUser?.sendEmailVerification()?.await()
                startResendCooldown(60)
            } catch (_: Exception) {
                // userul poate retrimite din VerifyEmailScreen
            }
            _uiState.value = AuthUiState.NavigateVerifyEmail
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
                resolvePostAuth()
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
            }
        }
    }

    fun logout() {
        auth.signOut()
        _uiState.value = AuthUiState.NavigateLogin
        _onboardingData.value = OnboardingData()
        _onboardingStep.value = 0
        _loadedProfile.value = null   // don't let the previous user's profile leak to the next login
    }

    fun resetState() { _uiState.value = AuthUiState.Idle }

    /** Called by LoginScreen when the Google Sign-In intent fails (not a user cancel). */
    fun reportGoogleSignInError() { _uiState.value = AuthUiState.Error(R.string.error_google_signin) }

    fun nextStep() { _onboardingStep.value = (_onboardingStep.value + 1).coerceAtMost(2) }
    fun prevStep() { _onboardingStep.value = (_onboardingStep.value - 1).coerceAtLeast(0) }

    fun updateOnboardingData(update: OnboardingData.() -> OnboardingData) {
        _onboardingData.value = _onboardingData.value.update()
    }

    /** Explicitly called by user on Step 3. */
    fun calculateTdee() {
        val d = _onboardingData.value
        val age = d.age.toIntOrNull() ?: return
        val heightCm = d.heightCm.toIntOrNull() ?: return
        val currentKg = d.currentWeightKg.replace(",", ".").toFloatOrNull() ?: return
        val targetKg = d.targetWeightKg.replace(",", ".").toFloatOrNull() ?: currentKg

        viewModelScope.launch {
            _onboardingData.value = d.copy(isTdeeLoading = true, calculatedCalorieGoal = null)
            try {
                val dto = ProfileDto(d.name, age, d.gender.name, heightCm, currentKg, targetKg, d.activityLevel.name)
                val response = profileApi.calculateTdee(dto)
                _onboardingData.value = _onboardingData.value.copy(
                    calculatedCalorieGoal = if (response.isSuccessful) response.body()?.calorieGoal else null,
                    isTdeeLoading = false
                )
            } catch (_: Exception) {
                _onboardingData.value = _onboardingData.value.copy(isTdeeLoading = false)
            }
        }
    }

    /** Saves profile to backend and navigates home. */
    fun submitProfile() {
        val d = _onboardingData.value
        val dto = d.toProfileDtoOrNull() ?: return

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                profileApi.saveProfile(dto)
                saveSnapshot(dto)
                _loadedProfile.value = dto.toUserProfile()
                _uiState.value = AuthUiState.NavigateHome
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
            }
        }
    }

    /** Persists profile edits from ProfileScreen with save state feedback. */
    fun saveProfileEdit(profile: UserProfile) {
        _loadedProfile.value = profile
        _profileSaveState.value = ProfileSaveState.Saving
        viewModelScope.launch {
            runCatching { profileApi.saveProfile(profile.toProfileDto()) }
                .onSuccess {
                    saveSnapshot(profile.toProfileDto())
                    _profileUpdates.tryEmit(Unit)
                    _profileSaveState.value = ProfileSaveState.Success
                }
                .onFailure {
                    _profileSaveState.value = ProfileSaveState.Error(R.string.error_profile_save)
                }
        }
    }

    private suspend fun resolvePostAuth() {
        val uid = currentUser()?.uid
        _uiState.value = if (uid == null) AuthUiState.Offline else fetchProfileState(uid)
    }

    /**
     * GET /profile: 200 caches the profile and goes home; 404 means no profile yet (onboarding);
     * network/5xx fall back to the cached profile of this account, otherwise [AuthUiState.Offline].
     */
    private suspend fun fetchProfileState(uid: String): AuthUiState {
        val unreachable = try {
            val response = profileApi.getProfile()
            when {
                response.isSuccessful -> {
                    response.body()?.let {
                        _loadedProfile.value = it.toUserProfile()
                        // A cache write failure must never turn a 200 into "offline".
                        runCatching { snapshots?.save(uid, it, null) }
                    }
                    return AuthUiState.NavigateHome
                }
                response.code() == 404 -> return AuthUiState.NavigateOnboarding
                response.code() >= 500 -> true
                else -> false
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // IOException, or a token refresh failure from AuthInterceptor: no usable connection.
            true
        }
        if (unreachable) {
            val cached = runCatching { snapshots?.read(uid)?.profile }.getOrNull()
            if (cached != null) {
                _loadedProfile.value = cached.toUserProfile()
                return AuthUiState.NavigateHome
            }
        }
        return AuthUiState.Offline
    }

    private fun saveSnapshot(profile: ProfileDto) {
        val uid = currentUser()?.uid ?: return
        viewModelScope.launch { snapshots?.save(uid, profile, null) }
    }
}
