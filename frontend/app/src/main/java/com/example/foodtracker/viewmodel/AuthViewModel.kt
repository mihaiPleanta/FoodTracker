package com.example.foodtracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    object NavigateLogin : AuthUiState()
    object NavigateHome : AuthUiState()
    object NavigateOnboarding : AuthUiState()
}

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

    /** Called by SplashScreen on startup. */
    fun checkAuthState() {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                _uiState.value = AuthUiState.NavigateLogin
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
                resolvePostAuth()
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
                _uiState.value = AuthUiState.NavigateOnboarding
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(mapFirebaseError(e))
            }
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

    /** Persists profile edits from ProfileScreen. Fire-and-forget. */
    fun saveProfileEdit(profile: UserProfile) {
        _loadedProfile.value = profile
        viewModelScope.launch {
            runCatching { profileApi.saveProfile(profile.toProfileDto()) }
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
