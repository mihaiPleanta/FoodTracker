package com.example.foodtracker

import com.example.foodtracker.viewmodel.OnboardingData
import com.example.foodtracker.viewmodel.toProfileDtoOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingProfileDtoTest {
    @Test
    fun onboarding_creatineFlagReachesDto() {
        val d = OnboardingData(name = "A", age = "22", heightCm = "180", currentWeightKg = "78,5", takesCreatine = true)
        val dto = d.toProfileDtoOrNull()!!
        assertTrue(dto.takesCreatine)
        assertNull(dto.hydrationGoalOverrideL)
        assertEquals(78.5f, dto.targetWeightKg)
    }

    @Test
    fun onboarding_invalidAge_returnsNull() {
        assertNull(OnboardingData(age = "x").toProfileDtoOrNull())
    }
}
