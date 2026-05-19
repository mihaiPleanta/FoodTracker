package com.example.foodtracker.util

object CategoryEmojiMapper {
    private val MAP: List<Pair<String, String>> = listOf(
        "bananas" to "🍌",
        "apples" to "🍎",
        "oranges" to "🍊",
        "berries" to "🫐",
        "fruits" to "🍎",
        "yogurts" to "🫙",
        "cheeses" to "🧀",
        "milks" to "🥛",
        "dairies" to "🥛",
        "eggs" to "🥚",
        "poultries" to "🍗",
        "beefs" to "🥩",
        "meats" to "🥩",
        "fish" to "🐟",
        "seafood" to "🦐",
        "breads" to "🍞",
        "pastas" to "🍝",
        "rices" to "🍚",
        "cereals" to "🥣",
        "vegetables" to "🥦",
        "potatoes" to "🥔",
        "chocolates" to "🍫",
        "biscuits" to "🍪",
        "snacks" to "🍪",
        "candies" to "🍬",
        "waters" to "💧",
        "juices" to "🧃",
        "alcoholic-beverages" to "🍷",
        "beverages" to "🥤",
        "oils" to "🫒",
        "sauces" to "🥫",
        "soups" to "🥣",
    )

    const val DEFAULT_EMOJI: String = "🍽️"

    fun emojiFor(categories: List<String>): String {
        for (cat in categories) {
            for ((key, emoji) in MAP) {
                if (cat.contains(key, ignoreCase = true)) return emoji
            }
        }
        return DEFAULT_EMOJI
    }
}
