package com.example.foodtracker.util.label

/**
 * Simulated ML Kit Text Recognition output for real-world-style nutrition labels.
 *
 * ML Kit returns one line per run of text; when the gap between a nutrient name and its
 * values is wide (multi-column tables) the name and each column value arrive as separate
 * lines on the same vertical band, with slightly jittered `top` values. Narrow single-column
 * labels often come back as one line per row ("Grăsimi 17 g").
 */
object LabelFixtures {

    private const val LINE_HEIGHT = 28
    private const val CHAR_WIDTH = 14

    /** A line whose box width is proportional to its text length, like real OCR boxes. */
    private fun line(text: String, left: Int, top: Int): OcrLine =
        OcrLine(text, left, top, left + text.length * CHAR_WIDTH, top + LINE_HEIGHT)

    /** One column of values, names and values as separate lines. */
    val singleColumnRo: List<OcrLine> = listOf(
        line("Declarație nutrițională", 20, 0),
        line("Valori medii per 100 g", 20, 40),
        line("Valoare energetică", 20, 80), line("1891 kJ / 452 kcal", 300, 82),
        line("Grăsimi", 20, 120), line("17 g", 300, 121),
        line("din care acizi grași saturați", 20, 160), line("8,2 g", 440, 158),
        line("Glucide", 20, 200), line("68 g", 300, 202),
        line("din care zaharuri", 20, 240), line("30 g", 300, 240),
        line("Fibre", 20, 280), line("2,4 g", 300, 280),
        line("Proteine", 20, 320), line("6,1 g", 300, 321),
        line("Sare", 20, 360), line("0,9 g", 300, 360),
    )

    /** Columns "100 g" and "Porție 30 g"; the energy cell wraps kJ / kcal onto two lines. */
    val per100AndServing: List<OcrLine> = listOf(
        line("Informații nutriționale", 20, 0),
        line("100 g", 380, 40), line("Porție 30 g", 520, 41),
        line("Energie", 20, 80), line("1891 kJ", 380, 80), line("567 kJ", 520, 81),
        line("452 kcal", 380, 112), line("136 kcal", 520, 111),
        line("Grăsimi", 20, 150), line("17 g", 380, 150), line("5,1 g", 520, 151),
        line("din care saturați", 20, 190), line("8,2 g", 380, 190), line("2,5 g", 520, 190),
        line("Glucide", 20, 230), line("68 g", 380, 231), line("20 g", 520, 230),
        line("din care zaharuri", 20, 270), line("30 g", 380, 270), line("9,0 g", 520, 272),
        line("Proteine", 20, 310), line("6,1 g", 380, 310), line("1,8 g", 520, 309),
        line("Sare", 20, 350), line("0,9 g", 380, 350), line("0,27 g", 520, 350),
    )

    /** Like [per100AndServing], but OCR missed the 100 g protein cell; only the portion cell is left. */
    val missing100gCell: List<OcrLine> = per100AndServing.filterNot { it.text == "6,1 g" }

    /** Columns "per 100 g" and "%RI*", plus the RI footnote that mentions 2000 kcal. */
    val per100AndRi: List<OcrLine> = listOf(
        line("Valori nutriționale medii", 20, 0),
        line("per 100 g", 360, 40), line("%RI*", 540, 40),
        line("Energie", 20, 80), line("1891 kJ / 452 kcal", 360, 80), line("23%", 640, 81),
        line("Grăsimi", 20, 120), line("17 g", 360, 120), line("24 %", 640, 120),
        line("din care saturați", 20, 160), line("8,2 g", 360, 161), line("41%", 640, 160),
        line("Glucide", 20, 200), line("68 g", 360, 200), line("26%", 640, 199),
        line("din care zaharuri", 20, 240), line("30 g", 360, 240), line("33%", 640, 240),
        line("Proteine", 20, 280), line("6,1 g", 360, 281), line("12%", 640, 280),
        line("Sare", 20, 320), line("0,9 g", 360, 320), line("15%", 640, 320),
        line("*Aport de referință pentru un adult mediu (8400 kJ/2000 kcal)", 20, 370),
    )

    /** Energy printed only in kJ (1046 kJ = 250 kcal). */
    val kjOnly: List<OcrLine> = listOf(
        line("Valori nutriționale / 100 g", 20, 0),
        line("Energie 1046 kJ", 20, 40),
        line("Grăsimi 9 g", 20, 80),
        line("Glucide 35 g", 20, 120),
        line("Proteine 4,5 g", 20, 160),
    )

    /** kJ-only with a thousands separator: "1.046 kJ" is 1046 kJ, never 1.046 kJ (= 0 kcal). */
    private fun kjOnlyWith(energy: String): List<OcrLine> = listOf(
        line("Valori nutriționale / 100 g", 20, 0),
        line("Energie $energy", 20, 40),
        line("Grăsimi 9 g", 20, 80),
        line("Glucide 35 g", 20, 120),
        line("Proteine 4,5 g", 20, 160),
    )
    val kjThousandsDot: List<OcrLine> = kjOnlyWith("1.046 kJ")
    val kjThousandsComma: List<OcrLine> = kjOnlyWith("1,046 kJ")
    val kjThousandsSpace: List<OcrLine> = kjOnlyWith("1 046 kJ")
    val kjThousandsNarrowSpace: List<OcrLine> = kjOnlyWith("1\u202F046 kJ")
    /** kJ-only below 10 kcal: too likely a misread to fill in silently. */
    val kjOnlyTiny: List<OcrLine> = kjOnlyWith("17 kJ")

    /** "<0,5 g" means "less than 0.5 g". */
    val lessThan: List<OcrLine> = listOf(
        line("Valori nutriționale per 100 ml", 20, 0),
        line("Energie 17 kJ / 4 kcal", 20, 40),
        line("Grăsimi <0,5 g", 20, 80),
        line("Glucide 0,9 g", 20, 120),
        line("Proteine 0 g", 20, 160),
    )

    /** UK/EN label with "per 100g" and "per 30g serving" columns. */
    val englishLabel: List<OcrLine> = listOf(
        line("Nutrition Information", 20, 0),
        line("Typical values", 20, 40), line("per 100g", 360, 40), line("per 30g serving", 520, 40),
        line("Energy", 20, 80), line("1046 kJ", 360, 80), line("314 kJ", 520, 80),
        line("250 kcal", 360, 112), line("75 kcal", 520, 112),
        line("Fat", 20, 150), line("9.0 g", 360, 150), line("2.7 g", 520, 151),
        line("of which saturates", 20, 190), line("3.1 g", 360, 190), line("0.9 g", 520, 190),
        line("Carbohydrate", 20, 230), line("35 g", 360, 230), line("10.5 g", 520, 231),
        line("of which sugars", 20, 270), line("20 g", 360, 270), line("6.0 g", 520, 270),
        line("Fibre", 20, 310), line("2.0 g", 360, 310), line("0.6 g", 520, 310),
        line("Protein", 20, 350), line("4.5 g", 360, 350), line("1.4 g", 520, 351),
        line("Salt", 20, 390), line("0.80 g", 360, 390), line("0.24 g", 520, 390),
    )

    /** Typical OCR confusions: letter O for zero, lowercase l for one. */
    val ocrNoise: List<OcrLine> = listOf(
        line("Valori nutriționale la 100 g", 20, 0),
        line("Valoare energetică 1569 kJ / 375 kcal", 20, 40),
        line("Grăsimi l7 g", 20, 80),
        line("Glucide 42,3 g", 20, 120),
        line("Proteine 1O,5 g", 20, 160),
    )

    /** The kcal value sits on the line below "Valoare energetică 1891 kJ". */
    val energySplitAcrossRows: List<OcrLine> = listOf(
        line("Declarație nutrițională per 100 g", 20, 0),
        line("Valoare energetică 1891 kJ", 20, 40),
        line("452 kcal", 270, 72),
        line("Grăsimi 17 g", 20, 110),
        line("Glucide 68 g", 20, 150),
        line("Proteine 6,1 g", 20, 190),
    )

    /** Per-piece column with the piece weight given in a footnote. */
    val pieceServing: List<OcrLine> = listOf(
        line("Valori nutriționale medii", 20, 0),
        line("100 g", 360, 40), line("1 biscuit*", 500, 40),
        line("Energie", 20, 80), line("2010 kJ / 480 kcal", 360, 80), line("241 kJ / 58 kcal", 600, 80),
        line("Grăsimi", 20, 120), line("22 g", 360, 120), line("2,6 g", 600, 120),
        line("Glucide", 20, 160), line("63 g", 360, 160), line("7,6 g", 600, 160),
        line("Proteine", 20, 200), line("7 g", 360, 200), line("0,8 g", 600, 200),
        line("*1 biscuit = 12 g", 20, 250),
    )

    /** "Proteine 4,50 g" read without the comma: 450 g per 100 g is impossible. */
    val implausible: List<OcrLine> = listOf(
        line("Valori nutriționale la 100 g", 20, 0),
        line("Energie 1550 kJ / 370 kcal", 20, 40),
        line("Grăsimi 12 g", 20, 80),
        line("Glucide 55 g", 20, 120),
        line("Proteine 450 g", 20, 160),
    )

    /** Each macro is individually plausible, but together they exceed 100 g per 100 g. */
    val macroSumOver100: List<OcrLine> = listOf(
        line("Valori nutriționale la 100 g", 20, 0),
        line("Energie 1891 kJ / 452 kcal", 20, 40),
        line("Grăsimi 17 g", 20, 80),
        line("Glucide 68 g", 20, 120),
        line("Proteine 61 g", 20, 160),
    )

    /** Ingredient list wrapped so that a line starts with "grăsimi vegetale … 20", above the table. */
    val ingredientsFatAboveTable: List<OcrLine> = listOf(
        line("Ingrediente: făină de grâu, zahăr,", 20, 0),
        line("grăsimi vegetale (palmier) 20, sare.", 20, 36),
        line("Valori nutriționale medii", 20, 90),
        line("100 g", 380, 130), line("Porție 30 g", 520, 131),
        line("Energie", 20, 170), line("1891 kJ / 452 kcal", 380, 170), line("567 kJ / 136 kcal", 640, 171),
        line("Grăsimi", 20, 210), line("17 g", 380, 210), line("5,1 g", 640, 211),
        line("Glucide", 20, 250), line("68 g", 380, 250), line("20 g", 640, 250),
        line("Proteine", 20, 290), line("6,1 g", 380, 291), line("1,8 g", 640, 290),
    )

    /** Ingredient line starting with "proteine din lapte 3,5"; single-column table without a 100 g header. */
    val ingredientsProteinNoHeader: List<OcrLine> = listOf(
        line("Ingrediente: lapte smântânit, zahăr,", 20, 0),
        line("proteine din lapte 3,5, cacao 2%.", 20, 36),
        line("Energie 1550 kJ / 370 kcal", 20, 90),
        line("Grăsimi 12 g", 20, 130),
        line("Glucide 55 g", 20, 170),
        line("Proteine 6,1 g", 20, 210),
    )

    /** Jam label: a prose sentence mentions "100 g" above a two-column table (100 g + portion). */
    val jamProse100g: List<OcrLine> = listOf(
        line("Preparat din 50 g fructe la 100 g produs", 20, 0),
        line("Valori nutriționale", 20, 50),
        line("100 g", 300, 90), line("Porție 20 g", 440, 90),
        line("Energie", 20, 130), line("1046 kJ / 250 kcal", 300, 130), line("209 kJ / 50 kcal", 440, 131),
        line("Grăsimi", 20, 210), line("0,2 g", 300, 210), line("0 g", 440, 211),
        line("Glucide", 20, 250), line("60 g", 300, 250), line("12 g", 440, 250),
        line("Proteine", 20, 290), line("0,5 g", 300, 290), line("0,1 g", 440, 290),
    )

    /** Piece unit with diacritics must keep its spelling. */
    val pieceWithDiacritics: List<OcrLine> = listOf(
        line("Valori nutriționale la 100 g", 20, 0),
        line("Energie 1550 kJ / 370 kcal", 20, 40),
        line("Grăsimi 12 g", 20, 80),
        line("Glucide 55 g", 20, 120),
        line("Proteine 6 g", 20, 160),
        line("1 bucată = 20 g", 20, 210),
    )

    /** Back of the pack without a nutrition table; the ingredient list even mentions "grăsimi". */
    val garbage: List<OcrLine> = listOf(
        line("Ingrediente: făină de grâu 45%, zahăr, grăsimi vegetale", 20, 0),
        line("(palmier, shea), cacao 4%, emulsifiant: lecitine.", 20, 40),
        line("A se păstra la loc uscat și răcoros.", 20, 80),
        line("Lot: L2304 Expiră la: 12.2027", 20, 120),
    )
}
