package com.example.foodtracker

import com.example.foodtracker.model.FoodLogCopyItemDto
import com.example.foodtracker.model.FoodLogCopyRequestDto
import com.example.foodtracker.model.FoodLogSummaryDto
import com.example.foodtracker.model.MEAL_ORDER
import com.example.foodtracker.model.mealNameFromBackend
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyDtoTest {
    private val gson = Gson()

    @Test
    fun copyRequest_serializesSnakeCase() {
        val json = gson.toJson(
            FoodLogCopyRequestDto("2026-11-01", listOf(FoodLogCopyItemDto(7L, 150, "slice", 2f))),
        )
        assertTrue(json.contains("\"to_date\""))
        assertTrue(json.contains("\"source_id\""))
        assertTrue(json.contains("\"unit_qty\""))
    }

    @Test
    fun summary_deserializes() {
        val json = """{"days":[{"date":"2026-10-30","meals":{"dinner":{"count":3,"kcal":640}}}]}"""
        val dto = gson.fromJson(json, FoodLogSummaryDto::class.java)
        assertEquals(640, dto.days[0].meals["dinner"]!!.kcal)
        assertEquals(3, dto.days[0].meals["dinner"]!!.count)
    }

    @Test
    fun mealNames() {
        assertEquals("Dinner", mealNameFromBackend("DINNER"))
        assertEquals("Snacks", mealNameFromBackend("???"))
        assertEquals(listOf("Breakfast", "Lunch", "Dinner", "Snacks"), MEAL_ORDER)
    }
}
