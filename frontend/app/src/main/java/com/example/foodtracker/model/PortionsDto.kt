package com.example.foodtracker.model

import com.google.gson.annotations.SerializedName

enum class UnitSource { LABEL, ESTIMATE, USER }

data class PortionUnit(val unit: String, val grams: Float, val source: UnitSource)

data class LastPortion(val unit: String?, val unitQty: Float?, val grams: Int)

data class Portions(
    val units: List<PortionUnit>,
    val gramPresets: List<Int>,
    val presetsCustom: Boolean,
    val last: LastPortion?,
)

data class PortionUnitDto(
    val unit: String,
    val grams: Float,
    val source: String? = null,
) {
    fun toDomain(): PortionUnit = PortionUnit(
        unit = unit,
        grams = grams,
        source = when (source) {
            "label" -> UnitSource.LABEL
            "user" -> UnitSource.USER
            else -> UnitSource.ESTIMATE
        },
    )
}

data class LastPortionDto(
    val unit: String? = null,
    @SerializedName("unit_qty") val unitQty: Float? = null,
    val grams: Int,
) {
    fun toDomain(): LastPortion = LastPortion(unit, unitQty, grams)
}

data class PortionsDto(
    val units: List<PortionUnitDto> = emptyList(),
    @SerializedName("gram_presets") val gramPresets: List<Int> = emptyList(),
    @SerializedName("presets_custom") val presetsCustom: Boolean = false,
    val last: LastPortionDto? = null,
) {
    fun toDomain(): Portions = Portions(
        units = units.map { it.toDomain() },
        gramPresets = gramPresets,
        presetsCustom = presetsCustom,
        last = last?.toDomain(),
    )
}

/** Body and response of PUT /portions/{barcode}/units. */
data class PortionUnitInDto(val unit: String, val grams: Float)

/** Body and response of PUT /portions/{barcode}/presets. */
data class GramPresetsDto(val grams: List<Int>)
