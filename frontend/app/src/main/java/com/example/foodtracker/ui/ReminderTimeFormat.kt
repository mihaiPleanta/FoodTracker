package com.example.foodtracker.ui

/** "08:05" (24h) or "8:05 AM" (12h) for a time stored as minutes since midnight. */
fun formatMinuteOfDay(minuteOfDay: Int, is24Hour: Boolean): String {
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    if (is24Hour) return "%02d:%02d".format(hour, minute)
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(hour12, minute, suffix)
}
