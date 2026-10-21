package com.example.foodtracker.model

/** Maps the UI/navigation meal name ("Breakfast") to the backend enum ("BREAKFAST"). */
fun mealKeyToBackend(meal: String): String = when (meal) {
    "Breakfast" -> "BREAKFAST"
    "Lunch"     -> "LUNCH"
    "Dinner"    -> "DINNER"
    "Snacks"    -> "SNACKS"
    else        -> "BREAKFAST"
}
