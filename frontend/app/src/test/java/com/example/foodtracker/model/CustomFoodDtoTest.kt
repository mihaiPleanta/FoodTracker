package com.example.foodtracker.model

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class CustomFoodDtoTest {
    private fun item(barcode: String) = FoodItem(
        barcode = barcode, name = "x", per100g = 1,
        protein100g = 0f, carbs100g = 0f, fat100g = 0f,
    )

    private fun err(code: Int, body: String) = HttpException(
        Response.error<Any>(code, body.toResponseBody("application/json".toMediaType()))
    )

    @Test fun isCustom_dependsOnPrefix() {
        assertTrue(item("custom-3").isCustom)
        assertFalse(item("5941234567890").isCustom)
        assertFalse(item("usda-apple").isCustom)
    }

    @Test fun customFoodDto_toFoodItem_keepsKeyAndTruncatesOnce() {
        val f = CustomFoodDto(
            id = 7, key = "custom-7", name = "n", kcal100g = 452.9f,
            protein100g = 1f, carbs100g = 2f, fat100g = 3f,
        ).toFoodItem()
        assertEquals(452, f.per100g)
        assertEquals("custom-7", f.barcode)
    }

    @Test fun parseBarcodeTaken_readsExistingId() {
        val body = """{"detail":{"code":"barcode_taken","existing_id":5}}"""
        assertEquals(5, parseBarcodeTaken(err(409, body)))
        assertNull(parseBarcodeTaken(err(500, body)))
        assertNull(parseBarcodeTaken(err(409, "nu e json")))
    }
}
