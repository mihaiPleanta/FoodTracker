package com.example.foodtracker.streak

/** Must match `MILESTONES` in backend/services/streak.py; beyond the last, every 100. */
val STREAK_MILESTONES: List<Int> = listOf(3, 7, 14, 30, 50, 100, 180, 365)

private val LAST_FIXED = STREAK_MILESTONES.last()

/** Highest milestone m with old < m <= new, or null (also when old is null: first load). */
fun crossedMilestone(old: Int?, new: Int): Int? {
    if (old == null) return null
    val beyond = if (new > LAST_FIXED) (new / 100) * 100 else 0
    val highest = maxOf(
        STREAK_MILESTONES.lastOrNull { it <= new } ?: 0,
        if (beyond > LAST_FIXED) beyond else 0,
    )
    return highest.takeIf { it > 0 && it > old }
}

/** Progress from the previous milestone (0 below the first) to [next], in 0..1. */
fun milestoneProgress(current: Int, next: Int): Float {
    val prev = if (next > LAST_FIXED) maxOf(LAST_FIXED, (next - 1) / 100 * 100)
    else STREAK_MILESTONES.lastOrNull { it < next } ?: 0
    if (next <= prev) return 1f
    return ((current - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
}
