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
    private val ingredientMarkers = listOf("ingrediente", "ingredients")
    private val tableTitleMarkers = listOf("valori nutritionale", "declaratie nutritionala", "nutrition")

    /** A header line longer than this is prose ("Preparat din 50 g fructe la 100 g produs"). */
    private const val MAX_HEADER_TOKENS = 6

    private class Token(val text: String, val x: Float, val line: Line)

    /**
     * [text] is normalized (ASCII, OCR digit fixes); [raw] is the lowercased original with the
     * same length, so a match range in [text] can be read back with diacritics from [raw].
     */
    private class Line(val text: String, val raw: String, val left: Int, val right: Int, val top: Int, val bottom: Int) {
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
        val raw: String = lines.joinToString(" ") { it.raw }
        val tokens: List<Token> = lines.flatMap { it.tokens }
    }

    private class Num(val value: Float, val unit: String, val x: Float, val line: Line, val nextToken: String?)

    /** Where the per-100 g column is: the header token's x, its line, and the columns to its right. */
    private class Columns(val row: Row, val x100: Float, val header: Line, val others: List<Line>)

    private fun Line.overlaps(other: Line): Boolean = left < other.right && other.left < right

    fun parse(lines: List<OcrLine>): ParsedLabel {
        val rows = groupRows(lines.filter { it.text.isNotBlank() }.map(::normalizeLine))
        if (rows.isEmpty()) return ParsedLabel(null, null, null, null, null, null)

        val inIngredients = ingredientBlock(rows)
        val columns = findColumns(rows, inIngredients)
        // With a known table header, nutrient rows above it are prose, not the table.
        val firstTableRow = columns?.let { rows.indexOf(it.row) + 1 } ?: 0

        var kcal: Float? = null
        var fat: Float? = null
        var carbs: Float? = null
        var protein: Float? = null

        rows.forEachIndexed { index, row ->
            if (index < firstTableRow || inIngredients[index]) return@forEachIndexed
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
        val raw = Normalizer.normalize(line.text.lowercase(), Normalizer.Form.NFC)
        // Fold char by char so the ASCII text keeps the same length (and indices) as raw.
        val ascii = buildString(raw.length) {
            raw.forEach { c ->
                val folded = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
                    .replace(Regex("""\p{Mn}+"""), "")
                append(if (folded.length == 1) folded[0] else c)
            }
        }
        // Fix OCR letter/digit confusions only inside tokens that start like a number.
        val fixed = Regex("""\S+""").replace(ascii) { m ->
            val parts = numericCoreRegex.matchEntire(m.value) ?: return@replace m.value
            val (lt, core, rest) = parts.destructured
            val digits = core.replace('o', '0').replace('l', '1').replace('i', '1').replace(',', '.')
            lt + digits + rest
        }
        return Line(fixed, raw, line.left, line.right, line.top, line.bottom)
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

    /**
     * Rows of an ingredient list: from a row mentioning "ingrediente" until the nutrition table
     * starts (a table title, a column header or the energy row). Their keywords ("grăsimi
     * vegetale", "proteine din lapte") are not table rows.
     */
    private fun ingredientBlock(rows: List<Row>): BooleanArray {
        val result = BooleanArray(rows.size)
        var inside = false
        rows.forEachIndexed { i, row ->
            if (ingredientMarkers.any { row.text.contains(it) }) inside = true
            else if (inside && (tableTitleMarkers.any { row.text.contains(it) } ||
                    header100(row) != null || nutrientOf(row) == Nutrient.ENERGY)
            ) inside = false
            result[i] = inside
        }
        return result
    }

    /** The "100 g" line of a row that looks like a column header: tabular or a short title. */
    private fun header100(row: Row): Pair<Line, Float>? {
        if (nutrientOf(row) != null) return null
        for (line in row.lines) {
            val match = per100Regex.find(line.text) ?: continue
            if (row.lines.size < 2 && line.tokens.size > MAX_HEADER_TOKENS) continue
            return line to line.xOf(match.range.first, match.range.last + 1)
        }
        return null
    }

    /** The first header-like "100 g"/"100 ml" row that has nutrient rows below it. */
    private fun findColumns(rows: List<Row>, inIngredients: BooleanArray): Columns? {
        rows.forEachIndexed { i, row ->
            if (inIngredients[i]) return@forEachIndexed
            val (line, x) = header100(row) ?: return@forEachIndexed
            val hasTableBelow = (i + 1 until rows.size).any { j -> !inIngredients[j] && nutrientOf(rows[j]) != null }
            if (!hasTableBelow) return@forEachIndexed
            return Columns(row, x, line, row.lines.filter { it !== line && it.left > line.left })
        }
        return null
    }

    // --- nutrient rows --------------------------------------------------------------------

    /** The nutrient a row is about: its first word(s) must be a keyword ("Grăsimi 17 g"). */
    private fun nutrientOf(row: Row): Nutrient? {
        val text = row.text.trimStart { !it.isLetterOrDigit() }
        if (subRowMarkers.any { text.contains(it) }) return null
        return keywords.entries.firstOrNull { (_, words) ->
            words.any { Regex("""^${Regex.escape(it)}\b""").containsMatchIn(text) }
        }?.key
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
        val raw = rows.joinToString("\n") { it.raw }

        // A named unit ("1 biscuit = 12 g") keeps its original spelling; generic portion forms
        // give no unit name (the UI supplies a localized default).
        pieceRegex.findAll(text).forEach { m ->
            val grams = m.groupValues[2].toFloatOrNull()?.takeIf(::plausibleServing) ?: return@forEach
            val word = m.groupValues[1]
            return grams to (if (word in genericServingWords) null else raw.substring(m.groups[1]!!.range))
        }
        portionRegex.findAll(text).forEach { m ->
            val grams = m.groupValues[1].toFloatOrNull()?.takeIf(::plausibleServing) ?: return@forEach
            return grams to null
        }
        perRegex.findAll(text).forEach { m ->
            // "per 100 g" names the reference column, not a serving.
            val grams = m.groupValues[1].toFloatOrNull()
                ?.takeIf { it != 100f && plausibleServing(it) } ?: return@forEach
            return grams to null
        }
        return null to null
    }

    private fun plausibleServing(grams: Float): Boolean = grams > 0f && grams <= MAX_SERVING_GRAMS
}
