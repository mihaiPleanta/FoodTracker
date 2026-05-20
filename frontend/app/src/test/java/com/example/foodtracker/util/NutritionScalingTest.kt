package com.example.foodtracker.util

import com.example.foodtracker.model.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionScalingTest {

    private val banana = FoodItem(
        barcode = "0000000000001",
        name = "Banana",
        brand = null,
        imageUrl = null,
        categories = emptyList(),
        per100g = 89,
        protein100g = 1f,
        carbs100g = 23f,
        fat100g = 0f,
    )

    @Test
    fun `scaleNutrition at 100g returns per-100g values`() {
        val result = scaleNutrition(banana, 100)
        assertEquals(89, result.kcal)
        assertEquals(1f, result.protein, 0.001f)
        assertEquals(23f, result.carbs, 0.001f)
        assertEquals(0f, result.fat, 0.001f)
    }

    @Test
    fun `scaleNutrition at 150g multiplies by 1_5`() {
        val result = scaleNutrition(banana, 150)
        assertEquals(133, result.kcal)
        assertEquals(1.5f, result.protein, 0.001f)
        assertEquals(34.5f, result.carbs, 0.001f)
        assertEquals(0f, result.fat, 0.001f)
    }

    @Test
    fun `scaleNutrition at 50g halves all macros`() {
        val result = scaleNutrition(banana, 50)
        assertEquals(44, result.kcal)
        assertEquals(0.5f, result.protein, 0.001f)
        assertEquals(11.5f, result.carbs, 0.001f)
        assertEquals(0f, result.fat, 0.001f)
    }

    @Test
    fun `scaleNutrition at 0g returns zeros`() {
        val result = scaleNutrition(banana, 0)
        assertEquals(0, result.kcal)
        assertEquals(0f, result.protein, 0.001f)
        assertEquals(0f, result.carbs, 0.001f)
        assertEquals(0f, result.fat, 0.001f)
    }

    @Test
    fun `parseGrams returns null for empty string`() {
        assertEquals(null, parseGrams(""))
    }

    @Test
    fun `parseGrams returns null for zero`() {
        assertEquals(null, parseGrams("0"))
    }

    @Test
    fun `parseGrams returns null for non-numeric`() {
        assertEquals(null, parseGrams("abc"))
    }

    @Test
    fun `parseGrams returns null for value above 2000`() {
        assertEquals(null, parseGrams("2001"))
        assertEquals(null, parseGrams("9999"))
    }

    @Test
    fun `parseGrams accepts boundary values 1 and 2000`() {
        assertEquals(1, parseGrams("1"))
        assertEquals(2000, parseGrams("2000"))
    }

    @Test
    fun `parseGrams accepts typical input 100`() {
        assertEquals(100, parseGrams("100"))
    }
}
