package com.example.foodtracker.model

enum class DayStatus { COMPLETE, INCOMPLETE }

/** Wire value of `GET /days/{day}` -> `status`; null (or anything unknown) means "unmarked". */
fun String?.toDayStatus(): DayStatus? = when (this) {
    "complete" -> DayStatus.COMPLETE
    "incomplete" -> DayStatus.INCOMPLETE
    else -> null
}

data class DayStatusUpdateDto(val complete: Boolean)

data class DayStatusResponseDto(val date: String, val status: String)
