package com.example.foodtracker.data.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class ListStringConverterTest {
    private val c = ListStringConverter()

    @Test fun roundTrip_preservesOrderAndValues() {
        val list = listOf("3017620422003", "banane", "fructe")
        assertEquals(list, c.toList(c.fromList(list)))
    }

    @Test fun emptyList_roundTrips() {
        assertEquals(emptyList<String>(), c.toList(c.fromList(emptyList())))
    }

    @Test fun emptyString_decodesToEmptyList() {
        assertEquals(emptyList<String>(), c.toList(""))
    }
}
