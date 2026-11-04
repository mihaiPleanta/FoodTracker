package com.example.foodtracker.data.journal

import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.LoggedFood
import com.example.foodtracker.model.toDto
import com.example.foodtracker.sync.LocalLog
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun LogEntity.toLocal(): LocalLog {
    val logged = JournalJson.decodeLogged(payloadJson)
        .copy(id = serverId, clientId = clientId, pending = pending, sortKey = sortKey)
    return LocalLog(clientId, serverId, date, meal, sortKey, logged, pending)
}

internal fun LocalLog.toEntity() =
    LogEntity(clientId, serverId, date, meal, sortKey, JournalJson.encodeLogged(logged), pending)

/** `created_at` in millis; naive timestamps are UTC (server default). Null when unparseable. */
internal fun parseCreatedAt(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrNull()
}

internal fun FoodLogDto.toLocal(): LocalLog {
    val cid = clientId.ifBlank { "srv-$id" }
    val key = parseCreatedAt(createdAt) ?: id
    return LocalLog(cid, id, logDate, meal, key, toLogged().copy(clientId = cid, sortKey = key), pending = false)
}

internal fun LoggedFood.toCreateBody(date: String, meal: String, clientId: String) = FoodLogCreateDto(
    logDate = date,
    meal = meal,
    grams = grams,
    barcode = food.barcode,
    name = food.name,
    brand = food.brand,
    imageUrl = food.imageUrl,
    categories = food.categories,
    kcal100g = food.per100g.toFloat(),
    protein100g = food.protein100g,
    carbs100g = food.carbs100g,
    fat100g = food.fat100g,
    ingredients = food.ingredients?.map { it.toDto() },
    unit = unit,
    unitQty = unitQty,
    clientId = clientId,
)

internal fun LoggedFood.toUpdateBody() = FoodLogUpdateDto(
    grams = grams,
    ingredients = food.ingredients?.map { it.toDto() },
    unit = unit,
    unitQty = unitQty,
)

/** Same rules as `POST /food-logs/copy`: a recipe with edited ingredients takes grams + per-100g from them,
 *  an unedited recipe keeps its source values; both drop the unit; anything else keeps the item's grams/unit and has `ingredients = null`. */
internal fun LoggedFood.toCopyBody(date: String, meal: String, clientId: String, ingredientsEdited: Boolean = false): FoodLogCreateDto {
    val base = toCreateBody(date, meal, clientId)
    val ing = food.ingredients ?: return base.copy(ingredients = null)
    // Unedited recipe: source grams, per-100g and ingredients as they are.
    if (!ingredientsEdited) return base.copy(unit = null, unitQty = null)
    if (ing.isEmpty()) return base.copy(unit = null, unitQty = null)
    val totalG = ing.sumOf { it.grams.toDouble() }
    fun per(x: Double) = if (totalG > 0) (x / totalG * 100).toFloat() else 0f
    return base.copy(
        grams = Math.round(totalG).toInt(),
        kcal100g = per(ing.sumOf { it.kcal.toDouble() }),
        protein100g = per(ing.sumOf { it.protein.toDouble() }),
        carbs100g = per(ing.sumOf { it.carbs.toDouble() }),
        fat100g = per(ing.sumOf { it.fat.toDouble() }),
        unit = null,
        unitQty = null,
    )
}

/** What the server will store for [this] create (mirror row before sync); kcal truncated like server rows. */
internal fun FoodLogCreateDto.toLoggedFood(): LoggedFood = FoodLogDto(
    id = 0, clientId = clientId.orEmpty(), logDate = logDate, meal = meal, grams = grams, barcode = barcode,
    name = name, brand = brand, imageUrl = imageUrl, categories = categories, kcal100g = kcal100g,
    protein100g = protein100g, carbs100g = carbs100g, fat100g = fat100g, ingredients = ingredients,
    unit = unit, unitQty = unitQty,
).toLogged().copy(id = null)
