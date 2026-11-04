package com.example.foodtracker.sync

import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.LoggedFood

internal fun createBody(date: String = "2026-11-04", grams: Int = 100) = FoodLogCreateDto(
    logDate = date, meal = "LUNCH", grams = grams, barcode = "123", name = "Oats",
    kcal100g = 380f, protein100g = 13f, carbs100g = 60f, fat100g = 7f, clientId = "c1",
)

internal fun q(op: SyncOp, seq: Long = 0, inFlight: Boolean = false) = QueuedOp(seq, op, inFlight)

internal fun log(cid: String, sortKey: Long, pending: Boolean = false, grams: Int = 100, serverId: Long? = null) = LocalLog(
    clientId = cid, serverId = serverId, date = "2026-11-04", meal = "LUNCH", sortKey = sortKey,
    logged = LoggedFood(
        FoodItem("123", "Oats", per100g = 380, protein100g = 13f, carbs100g = 60f, fat100g = 7f),
        grams, id = serverId, clientId = cid, pending = pending, sortKey = sortKey,
    ),
    pending = pending,
)
