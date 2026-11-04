package com.example.foodtracker.sync

import com.example.foodtracker.model.FoodItem
import com.example.foodtracker.model.FoodLogCreateDto
import com.example.foodtracker.model.FoodLogUpdateDto
import com.example.foodtracker.model.toDomain
import com.example.foodtracker.model.toDto
import com.example.foodtracker.model.totalGrams
import com.example.foodtracker.model.withIngredients

/** True when the create may be rewritten or cancelled: never sent in any form. */
private fun QueuedOp.isUnsentCreate(clientId: String) =
    !inFlight && !attempted && (op as? SyncOp.CreateLog)?.clientId == clientId

private fun QueuedOp.clientIdOrNull(): String? = when (val o = op) {
    is SyncOp.CreateLog -> o.clientId
    is SyncOp.UpdateLog -> o.clientId
    is SyncOp.DeleteLog -> o.clientId
    else -> null
}

private fun applyUpdateToCreate(body: FoodLogCreateDto, upd: FoodLogUpdateDto): FoodLogCreateDto {
    val withUnit = body.copy(grams = upd.grams, unit = upd.unit, unitQty = upd.unitQty)
    val ing = upd.ingredients
    if (ing.isNullOrEmpty()) return withUnit
    val food = FoodItem(
        barcode = body.barcode, name = body.name, brand = body.brand, imageUrl = body.imageUrl,
        categories = body.categories, per100g = body.kcal100g.toInt(),
        protein100g = body.protein100g, carbs100g = body.carbs100g, fat100g = body.fat100g,
    ).withIngredients(ing.map { it.toDomain() })
    return withUnit.copy(
        grams = ing.map { it.toDomain() }.totalGrams().toInt(),
        kcal100g = food.per100g.toFloat(),
        protein100g = food.protein100g,
        carbs100g = food.carbs100g,
        fat100g = food.fat100g,
        ingredients = food.ingredients?.map { it.toDto() },
    )
}

/** New queue after adding [op]; new entries get seq = 0. */
fun enqueue(queue: List<QueuedOp>, op: SyncOp): List<QueuedOp> {
    fun new() = QueuedOp(0, op)
    return when (op) {
        is SyncOp.UpdateLog -> {
            val createIdx = queue.indexOfFirst {
                it.isUnsentCreate(op.clientId)
            }
            if (createIdx >= 0) {
                val c = queue[createIdx]
                val create = c.op as SyncOp.CreateLog
                queue.toMutableList().also {
                    it[createIdx] = c.copy(op = create.copy(body = applyUpdateToCreate(create.body, op.body)))
                }
            } else {
                val updIdx = queue.indexOfFirst {
                    !it.inFlight && (it.op as? SyncOp.UpdateLog)?.clientId == op.clientId
                }
                if (updIdx >= 0) queue.toMutableList().also { it[updIdx] = queue[updIdx].copy(op = op) }
                else queue + new()
            }
        }
        is SyncOp.DeleteLog -> {
            val hasPendingCreate = queue.any { it.isUnsentCreate(op.clientId) }
            if (hasPendingCreate) {
                queue.filterNot { it.clientIdOrNull() == op.clientId }
            } else {
                queue.filterNot { !it.inFlight && it.op is SyncOp.UpdateLog && it.clientIdOrNull() == op.clientId } + new()
            }
        }
        is SyncOp.PutHydration -> queue.filterNot { !it.inFlight && (it.op as? SyncOp.PutHydration)?.date == op.date } + new()
        is SyncOp.PutWeight -> queue.filterNot { !it.inFlight && (it.op as? SyncOp.PutWeight)?.date == op.date } + new()
        is SyncOp.PutDayStatus -> queue.filterNot { !it.inFlight && (it.op as? SyncOp.PutDayStatus)?.date == op.date } + new()
        is SyncOp.CreateLog -> queue + new()
    }
}

/** After [rejected] is removed: also drop later ops for the same clientId if it was a CreateLog. */
fun dropDependents(queue: List<QueuedOp>, rejected: QueuedOp): List<QueuedOp> {
    val create = rejected.op as? SyncOp.CreateLog
    return queue.filter { it.seq != rejected.seq && (create == null || it.clientIdOrNull() != create.clientId) }
}
