package com.example.foodtracker

import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodItemDto
import com.example.foodtracker.model.RecentFoodDto
import com.example.foodtracker.model.mealKeyToBackend
import com.example.foodtracker.model.toDto
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickPicksDtoTest {

    private val food = FoodItem(
        barcode = "usda-oats", name = "Fulgi de ovăz", brand = null, imageUrl = null,
        categories = listOf("cereals"), per100g = 379, protein100g = 13.2f, carbs100g = 67.7f, fat100g = 6.5f,
    )

    @Test
    fun toDto_roundTripsThroughToDomain() {
        assertEquals(food, food.toDto().toDomain())
    }

    @Test
    fun toDto_sendsKcalAsFloat() {
        assertEquals(379f, food.toDto().kcal100g)
    }

    @Test
    fun recentDto_toDomain_keepsLastGrams() {
        val dto = RecentFoodDto(food = food.toDto(), lastGrams = 150)
        val recent = dto.toDomain()
        assertEquals(food, recent.food)
        assertEquals(150, recent.lastGrams)
    }

    @Test
    fun mealKeyToBackend_mapsUiNames() {
        assertEquals("BREAKFAST", mealKeyToBackend("Breakfast"))
        assertEquals("LUNCH", mealKeyToBackend("Lunch"))
        assertEquals("DINNER", mealKeyToBackend("Dinner"))
        assertEquals("SNACKS", mealKeyToBackend("Snacks"))
    }
}
