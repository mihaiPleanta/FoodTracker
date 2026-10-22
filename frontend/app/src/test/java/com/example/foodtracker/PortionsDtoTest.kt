package com.example.foodtracker

import com.example.foodtracker.model.PortionUnit
import com.example.foodtracker.model.PortionsDto
import com.example.foodtracker.model.UnitSource
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortionsDtoTest {
    private val gson = Gson()

    private fun json(source: String = "estimate", last: String = """{"unit":"tsp","unit_qty":2.0,"grams":14}""") =
        """{"units":[{"unit":"tsp","grams":7.0,"source":"$source"}],"gram_presets":[50,100],"presets_custom":true,"last":$last}"""

    @Test
    fun portionsJsonParses() {
        val p = gson.fromJson(json(), PortionsDto::class.java).toDomain()
        assertEquals(PortionUnit("tsp", 7f, UnitSource.ESTIMATE), p.units[0])
        assertEquals(listOf(50, 100), p.gramPresets)
        assertEquals(true, p.presetsCustom)
        assertEquals(2f, p.last?.unitQty)
        assertEquals("tsp", p.last?.unit)
        assertEquals(14, p.last?.grams)
    }

    @Test
    fun nullLastParsesToNull() {
        assertNull(gson.fromJson(json(last = "null"), PortionsDto::class.java).toDomain().last)
    }

    @Test
    fun knownSourcesMap() {
        assertEquals(UnitSource.LABEL, gson.fromJson(json("label"), PortionsDto::class.java).toDomain().units[0].source)
        assertEquals(UnitSource.USER, gson.fromJson(json("user"), PortionsDto::class.java).toDomain().units[0].source)
    }

    @Test
    fun unknownSourceFallsBackToEstimate() {
        assertEquals(UnitSource.ESTIMATE, gson.fromJson(json("weird"), PortionsDto::class.java).toDomain().units[0].source)
    }
}
