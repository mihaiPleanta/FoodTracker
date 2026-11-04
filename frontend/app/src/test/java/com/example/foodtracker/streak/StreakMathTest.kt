package com.example.foodtracker.streak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakMathTest {
    @Test fun nullOldIsNull() = assertNull(crossedMilestone(null, 7))
    @Test fun crossesSeven() = assertEquals(7, crossedMilestone(6, 7))
    @Test fun noCrossWhenAlreadyAt() = assertNull(crossedMilestone(7, 7))
    @Test fun picksHighestCrossed() = assertEquals(14, crossedMilestone(2, 14))
    @Test fun beyondLastFixed() = assertEquals(400, crossedMilestone(365, 400))
    @Test fun noMilestoneBetween() = assertNull(crossedMilestone(401, 450))
    @Test fun progressFromPrevious() = assertEquals(5f / 7f, milestoneProgress(12, 14), 0.0001f)
    @Test fun progressBelowFirst() = assertEquals(1f / 3f, milestoneProgress(1, 3), 0.0001f)
    @Test fun progressBeyond365() = assertEquals(0.5f, milestoneProgress(450, 500), 0.0001f)
}
