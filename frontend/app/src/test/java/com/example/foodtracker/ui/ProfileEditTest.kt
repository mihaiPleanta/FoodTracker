package com.example.foodtracker.ui

import com.example.foodtracker.model.ActivityLevel
import com.example.foodtracker.model.Gender
import com.example.foodtracker.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileEditTest {

    @Test fun editedProfile_keepsHydrationPrefs() {
        val base = UserProfile(takesCreatine = true, hydrationGoalOverrideL = 3.0f)
        val updated = editedProfile(
            base = base,
            name = "Ana",
            age = "30",
            gender = Gender.FEMALE,
            heightCm = "165",
            currentWeightKg = 60f,
            targetWeight = "58,5",
            activityLevel = ActivityLevel.ACTIVE,
        )
        assertEquals(
            UserProfile(
                name = "Ana", age = 30, gender = Gender.FEMALE, heightCm = 165,
                currentWeightKg = 60f, targetWeightKg = 58.5f, activityLevel = ActivityLevel.ACTIVE,
                takesCreatine = true, hydrationGoalOverrideL = 3.0f,
            ),
            updated
        )
    }

    @Test fun editedProfile_fallsBackOnBlankOrInvalidInput() {
        val base = UserProfile(name = "Mihai", age = 22, heightCm = 178, targetWeightKg = 75f)
        val updated = editedProfile(
            base = base, name = "  ", age = "x", gender = base.gender, heightCm = "",
            currentWeightKg = base.currentWeightKg, targetWeight = "abc", activityLevel = base.activityLevel,
        )
        assertEquals(base, updated)
    }
}
