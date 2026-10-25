package com.example.foodtracker

import com.example.foodtracker.model.GoalsDto
import com.example.foodtracker.model.HydrationGoal
import com.example.foodtracker.model.UserProfile
import com.example.foodtracker.model.toProfileDto
import com.example.foodtracker.model.toUserProfile
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HydrationGoalDtoTest {
    private val base = """"calorie_goal": 2200, "protein_goal_g": 150, "carbs_goal_g": 250, "fat_goal_g": 70, "mode": "MAINTENANCE""""

    @Test fun userProfile_roundTripKeepsHydrationPrefs() {
        val p = UserProfile(takesCreatine = true, hydrationGoalOverrideL = 3.0f)
        assertEquals(p, p.toProfileDto().toUserProfile())
    }

    @Test fun goalsDto_parsesHydrationBlock() {
        val json = """{$base, "hydration": {"goal_l": 2.8, "auto_l": 3.6, "is_manual": true, "weight_part_l": 2.63, "activity_bonus_l": 0.5, "creatine_bonus_l": 0.5}}"""
        val g = Gson().fromJson(json, GoalsDto::class.java).toDomain()
        assertEquals(HydrationGoal(2.8f, 3.6f, true, 2.63f, 0.5f, 0.5f), g.hydration)
    }

    @Test fun goalsDto_missingHydration_isNull() {
        val g = Gson().fromJson("{$base}", GoalsDto::class.java).toDomain()
        assertNull(g.hydration)
    }

    @Test fun userProfile_serializesHydrationPrefsToSnakeCase() {
        val json = Gson().toJson(UserProfile(takesCreatine = true, hydrationGoalOverrideL = 3.0f).toProfileDto())
        assertTrue(json, json.contains("\"takes_creatine\":true"))
        assertTrue(json, json.contains("\"hydration_goal_override_l\":3.0"))
    }
}
