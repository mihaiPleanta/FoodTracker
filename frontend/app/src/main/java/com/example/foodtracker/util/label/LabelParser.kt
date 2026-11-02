package com.example.foodtracker.util.label

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToInt

/** One line of recognized text with its bounding box (pixels, as ML Kit reports it). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Values read from a nutrition label, all per 100 g; `null` = not found or not trustworthy. */
data class ParsedLabel(
    val kcal: Float?,
    val protein: Float?,
    val carbs: Float?,
    val fat: Float?,
    val servingGrams: Float?,
    val servingUnit: String?,
) {
    val isEmpty: Boolean
        get() = kcal == null && protein == null && carbs == null && fat == null &&
            servingGrams == null && servingUnit == null
}

/**
 * Turns OCR lines of an EU nutrition table into per-100 g values.
 *
 * Guiding rule: when a value is missing, ambiguous or implausible, return `null` rather than
 * a guess — the user fills it in from the photo.
 */
object LabelParser {

    private const val MAX_KCAL = 900f
    private const val MAX_MACRO = 100f
    private const val MAX_SERVING_GRAMS = 1000f
    private const val KJ_PER_KCAL = 4.184f

    private enum class Nutrient { ENERGY, FAT, CARBS, PROTEIN }

    private val keywords = mapOf(
        Nutrient.ENERGY to listOf("valoare energetica", "energie", "energy"),
        Nutrient.FAT to listOf("grasimi", "lipide", "fat"),
        Nutrient.CARBS to listOf("glucide", "carbohidrati", "carbohydrate"),
        Nutrient.PROTEIN to listOf("proteine", "protein"),
    )
    private val subRowMarkers = listOf("din care", "saturat", "zahar", "of which", "saturates", "sugars")

    private val per100Regex = Regex("""(?<![\d.])100\s?(g|ml)\b""")
    private val tokenRegex = Regex("""[^\s/()*:;=,]+""")
    private val numericCoreRegex = Regex("""^(<?)([0-9oil.,]*\d[0-9oil.,]*)(.*)$""")
    private val numberTokenRegex = Regex("""^<?(\d+(?:\.\d+)?)(.*)$""")

    private val pieceRegex = Regex("""\b1\s+([a-z]+)\s*=\s*(\d+(?:\.\d+)?)\s*g\b""")
    private val portionRegex = Regex("""\bportie\s*[=:(]?\s*\(?\s*(\d+(?:\.\d+)?)\s*g\b""")
    private val perRegex = Regex("""\bper\s+(\d+(?:\.\d+)?)\s*g\b""")
    private val genericServingWords = setOf("portie", "portion", "serving")
    private const val DEFAULT_SERVING_UNIT = "porție"

    private class Token(val text: String, val x: Float, val line: Line)

    private class Line(val text: String, val left: Int, val right: Int, val top: Int, val bottom: Int) {
        val centerY: Float get() = (top + bottom) / 2f
        val height: Int get() = bottom - top

        /** Horizontal position of a character range, interpolated inside the line's box. */
        fun xOf(start: Int, end: Int): Float {
            if (text.isEmpty()) return (left + right) / 2f
            val mid = (start + end) / 2f
            return left + (right - left) * mid / text.length
        }

        val tokens: List<Token> by lazy {
            tokenRegex.findAll(text).map { Token(it.value, xOf(it.range.first, it.range.last + 1), this) }.toList()
        }
    }

    private class Row(val lines: List<Line>) {
        val text: String = lines.joinToString(" ") { it.text }
        val tokens: List<Token> = lines.flatMap { it.tokens }
    }

    private class Num(val value: Float, val unit: String, val x: Float, val line: Line, val nextToken: String?)

    /** Where the per-100 g column is: the header token's x, its line, and the columns to its right. */
    private class Columns(val x100: Float, val header: Line, val others: List<Line>)

    private fun Line.overlaps(other: Line): Boolean = left < other.right && other.left < right

    fun parse(lines: List<OcrLine>): ParsedLabel {
        val rows = groupRows(lines.filter { it.text.isNotBlank() }.map(::normalizeLine))
        if (rows.isEmpty()) return ParsedLabel(null, null, null, null, null, null)

        val columns = findColumns(rows)

        var kcal: Float? = null
        var fat: Float? = null
        var carbs: Float? = null
        var protein: Float? = null

        rows.forEachIndexed { index, row ->
            val nutrient = nutrientOf(row) ?: return@forEachIndexed
            when (nutrient) {
                Nutrient.ENERGY -> if (kcal == null) kcal = readEnergy(row, rows.getOrNull(index + 1), columns)
                Nutrient.FAT -> if (fat == null) fat = readMacro(row, columns)
                Nutrient.CARBS -> if (carbs == null) carbs = readMacro(row, columns)
                Nutrient.PROTEIN -> if (protein == null) protein = readMacro(row, columns)
            }
        }

        kcal = kcal?.takeIf { it in 0f..MAX_KCAL }
        fat = fat?.takeIf { it in 0f..MAX_MACRO }
        carbs = carbs?.takeIf { it in 0f..MAX_MACRO }
        protein = protein?.takeIf { it in 0f..MAX_MACRO }
        if ((fat ?: 0f) + (carbs ?: 0f) + (protein ?: 0f) > MAX_MACRO) {
            fat = null; carbs = null; protein = null
        }

        val (servingGrams, servingUnit) = readServing(rows)
        return ParsedLabel(kcal, protein, carbs, fat, servingGrams, servingUnit)
    }

    // --- normalization --------------------------------------------------------------------

    private fun normalizeLine(line: OcrLine): Line {
        val ascii = Normalizer.normalize(line.text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        // Fix OCR letter/digit confusions only inside tokens that start like a number.
        val fixed = Regex("""\S+""").replace(ascii) { m ->
            val parts = numericCoreRegex.matchEntire(m.value) ?: return@replace m.value
            val (lt, core, rest) = parts.destructured
            val digits = core.replace('o', '0').replace('l', '1').replace('i', '1').replace(',', '.')
            lt + digits + rest
        }
        return Line(fixed, line.left, line.right, line.top, line.bottom)
    }

    // --- layout ---------------------------------------------------------------------------

    /** Lines whose vertical centers are close (same table row) form one row, ordered left→right. */
    private fun groupRows(lines: List<Line>): List<Row> {
        val rows = mutableListOf<MutableList<Line>>()
        for (line in lines.sortedBy { it.centerY }) {
            val current = rows.lastOrNull()
            val anchor = current?.first()
            if (anchor != null &&
                abs(line.centerY - anchor.centerY) <= minOf(line.height, anchor.height) / 2f
            ) {
                current.add(line)
            } else {
                rows.add(mutableListOf(line))
            }
        }
        return rows.map { Row(it.sortedBy { line -> line.left }) }
    }

    /** The first non-nutrient row mentioning "100 g"/"100 ml" defines the column layout. */
    private fun findColumns(rows: List<Row>): Columns? {
        for (row in rows) {
            if (nutrientOf(row) != null) continue
            for (line in row.lines) {
                val match = per100Regex.find(line.text) ?: continue
                val x = line.xOf(match.range.first, match.range.last + 1)
                return Columns(x, line, row.lines.filter { it !== line && it.left > line.left })
            }
        }
        return null
    }

    // --- nutrient rows --------------------------------------------------------------------

    private fun nutrientOf(row: Row): Nutrient? {
        val text = row.text
        if (subRowMarkers.any { text.contains(it) }) return null
        val matches = keywords.filter { (_, words) ->
            words.any { Regex("""\b${Regex.escape(it)}""").containsMatchIn(text) }
        }.keys
        return matches.singleOrNull()
    }

    private fun numbers(row: Row): List<Num> = row.tokens.mapIndexedNotNull { i, token ->
        val m = numberTokenRegex.matchEntire(token.text) ?: return@mapIndexedNotNull null
        val value = m.groupValues[1].toFloatOrNull() ?: return@mapIndexedNotNull null
        Num(value, m.groupValues[2].trimEnd('.', '*'), token.x, token.line, row.tokens.getOrNull(i + 1)?.text)
    }

    private fun Num.isFollowedBy(unit: String): Boolean =
        this.unit.startsWith(unit) || (this.unit.isEmpty() && nextToken?.startsWith(unit) == true)

    /**
     * Picks the value in the per-100 g column: the candidate closest to the header token.
     * A candidate whose line sits under another column (portion, %RI) but not under the
     * 100 g header is rejected, so a missing 100 g cell never borrows the portion value.
     */
    private fun pick(candidates: List<Num>, columns: Columns?): Float? {
        if (candidates.isEmpty()) return null
        if (columns == null) return candidates.first().value
        val best = candidates.minByOrNull { abs(it.x - columns.x100) }!!
        val underOtherColumn = columns.others.any { best.line.overlaps(it) }
        if (underOtherColumn && !best.line.overlaps(columns.header)) return null
        return best.value
    }

    private fun readMacro(row: Row, columns: Columns?): Float? {
        val candidates = numbers(row).filter { n ->
            (n.unit.isEmpty() || n.unit == "g") &&
                !n.isFollowedBy("%") && !n.isFollowedBy("kcal") && !n.isFollowedBy("kj")
        }
        return pick(candidates, columns)
    }

    private fun readEnergy(row: Row, next: Row?, columns: Columns?): Float? {
        val nums = numbers(row)
        val kcalHere = nums.filter { it.isFollowedBy("kcal") }
        if (kcalHere.isNotEmpty()) return pick(kcalHere, columns)

        if (next != null && nutrientOf(next) == null) {
            val kcalNext = numbers(next).filter { it.isFollowedBy("kcal") }
            if (kcalNext.isNotEmpty()) return pick(kcalNext, columns)
        }

        val kj = pick(nums.filter { it.isFollowedBy("kj") }, columns) ?: return null
        return (kj / KJ_PER_KCAL).roundToInt().toFloat()
    }

    // --- serving --------------------------------------------------------------------------

    private fun readServing(rows: List<Row>): Pair<Float?, String?> {
        val text = rows.joinToString("\n") { it.text }

        pieceRegex.findAll(text).forEach { m ->
            val grams = m.groupValues[2].toFloatOrNull()?.takeIf(::plausibleServing) ?: return@forEach
            val word = m.groupValues[1]
            return grams to (if (word in genericServingWords) DEFAULT_SERVING_UNIT else word)
        }
        portionRegex.findAll(text).forEach { m ->
            val grams = m.groupValues[1].toFloatOrNull()?.takeIf(::plausibleServing) ?: return@forEach
            return grams to DEFAULT_SERVING_UNIT
        }
        perRegex.findAll(text).forEach { m ->
            // "per 100 g" names the reference column, not a serving.
            val grams = m.groupValues[1].toFloatOrNull()
                ?.takeIf { it != 100f && plausibleServing(it) } ?: return@forEach
            return grams to DEFAULT_SERVING_UNIT
        }
        return null to null
    }

    private fun plausibleServing(grams: Float): Boolean = grams > 0f && grams <= MAX_SERVING_GRAMS
}
