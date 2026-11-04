package com.example.foodtracker.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncOutcomeTest {
    @Test fun classifyMapsCodes() {
        listOf(200, 201, 204).forEach { assertEquals("$it", SyncOutcome.SUCCESS, classify(it)) }
        assertEquals(SyncOutcome.DROP_SILENT, classify(404))
        listOf(400, 403, 409, 422).forEach { assertEquals("$it", SyncOutcome.DROP_NOTIFY, classify(it)) }
        listOf(401, 500, 503, null).forEach { assertEquals("$it", SyncOutcome.RETRY, classify(it)) }
    }
}
