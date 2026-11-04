package com.example.foodtracker.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogsDtoTest {
    private val gson = Gson()

    @Test fun foodLogDtoParsesClientIdAndCreatedAt() {
        val json = """{"id":7,"client_id":"abc-123","created_at":"2026-11-04T10:15:00","log_date":"2026-11-04",
            "meal":"LUNCH","grams":150,"barcode":"b","name":"n","categories":[],"kcal_100g":100.0,"protein_100g":1.0,
            "carbs_100g":2.0,"fat_100g":3.0}"""
        val dto = gson.fromJson(json, FoodLogDto::class.java)
        assertEquals("abc-123", dto.clientId)
        assertEquals("2026-11-04T10:15:00", dto.createdAt)
        val logged = dto.toLogged()
        assertEquals("abc-123", logged.clientId)
        assertEquals(7L, logged.id)
        assertEquals(false, logged.pending)
    }

    @Test fun createDtoSerializesClientId() {
        val dto = FoodLogCreateDto(
            logDate = "2026-11-04", meal = "LUNCH", grams = 100, barcode = "b", name = "n",
            kcal100g = 1f, protein100g = 1f, carbs100g = 1f, fat100g = 1f, clientId = "cid-1",
        )
        val obj = gson.toJsonTree(dto).asJsonObject
        assertEquals("cid-1", obj.get("client_id").asString)
    }

    @Test fun daysRangeParsesGoalSnapshot() {
        val json = """{"days":[
            {"date":"2026-11-03","foods_by_meal":{},"hydration_liters":1.5,"weight_check_in":null,"status":"complete",
             "goals":{"calorie_goal":2000,"protein_goal_g":150,"carbs_goal_g":250,"fat_goal_g":65,"tdee":2300}},
            {"date":"2026-11-04","foods_by_meal":{},"hydration_liters":0.0,"weight_check_in":null,"status":null,"goals":null}]}"""
        val resp = gson.fromJson(json, DaysRangeResponseDto::class.java)
        assertEquals(2, resp.days.size)
        val d = resp.days[0]
        assertEquals("2026-11-03", d.date)
        assertEquals(1.5f, d.hydrationLiters, 0f)
        assertEquals("complete", d.status)
        assertEquals(2300, d.goals?.tdee)
        assertEquals(150, d.goals?.proteinGoalG)
        assertTrue(d.foodsByMeal.isEmpty())
        assertNull(resp.days[1].goals)
        assertNull(resp.days[1].weightCheckIn)
    }

    @Test fun goalsParseTdee() {
        val json = """{"calorie_goal":2000,"protein_goal_g":150,"carbs_goal_g":250,"fat_goal_g":65,"mode":"MAINTENANCE","tdee":2345}"""
        val dto = gson.fromJson(json, GoalsDto::class.java)
        assertEquals(2345, dto.toDomain().tdee)
        val old = gson.fromJson(json.replace(""","tdee":2345""", ""), GoalsDto::class.java)
        assertNull(old.toDomain().tdee)
    }
}
