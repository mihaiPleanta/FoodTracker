package com.example.foodtracker.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.foodtracker.R
import com.example.foodtracker.api.ProfileApi
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
}

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
    val calculatedCalorieGoal: Int? = null,
    val isTdeeLoading: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val profileApi = RetrofitInstance.retrofit.create(ProfileApi::class.java)

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
            val user = auth.currentUser
            if (user == null) {
                _uiState.value = AuthUiState.NavigateLogin
                return@launch
            }
            if (!user.isEmailVerified) {
                _uiState.value = AuthUiState.NavigateVerifyEmail
                return@launch
            }
            _uiState.value = AuthUiState.Loading
            _uiState.value = try {
                val response = profileApi.getProfile()
                if (response.isSuccessful) {
                    response.body()?.let { _loadedProfile.value = it.toUserProfile() }
                    AuthUiState.NavigateHome
                } else AuthUiState.NavigateOnboarding
            } catch (_: Exception) {
                AuthUiState.NavigateOnboarding
            }
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
        val age = d.age.toIntOrNull() ?: return
        val heightCm = d.heightCm.toIntOrNull() ?: return
        val currentKg = d.currentWeightKg.replace(",", ".").toFloatOrNull() ?: return
        val targetKg = d.targetWeightKg.replace(",", ".").toFloatOrNull() ?: currentKg

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val dto = ProfileDto(d.name, age, d.gender.name, heightCm, currentKg, targetKg, d.activityLevel.name)
                profileApi.saveProfile(dto)
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
                    _profileUpdates.tryEmit(Unit)
                    _profileSaveState.value = ProfileSaveState.Success
                }
                .onFailure {
                    _profileSaveState.value = ProfileSaveState.Error(R.string.error_profile_save)
                }
        }
    }

    private suspend fun resolvePostAuth() {
        _uiState.value = try {
            val response = profileApi.getProfile()
            if (response.isSuccessful) {
                response.body()?.let { _loadedProfile.value = it.toUserProfile() }
                AuthUiState.NavigateHome
            } else AuthUiState.NavigateOnboarding
        } catch (_: Exception) {
            AuthUiState.NavigateOnboarding
        }
    }
}
