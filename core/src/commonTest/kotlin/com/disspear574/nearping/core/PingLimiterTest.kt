package com.disspear574.nearping.core
import kotlin.test.Test
import kotlin.test.assertEquals
class PingLimiterTest {
    @Test fun burstAndIndependentUrgent() {
        val l = PingLimiter()
        for (t in listOf(100L, 103L, 106L, 109L, 112L)) {
            assertEquals(0L, l.remaining("a", false, t)); l.record("a", false, t)
        }
        assertEquals(30L, l.remaining("a", false, 112))
        assertEquals(1L, l.remaining("a", false, 141))
        assertEquals(0L, l.remaining("a", false, 142))
        assertEquals(0L, l.remaining("b", false, 112))
        assertEquals(0L, l.remaining("a", true, 112))
        l.record("a", true, 112)
        assertEquals(30L, l.remaining("a", true, 112))
    }
    @Test fun intervalAndWindowExpiry() {
        val l = PingLimiter()
        l.record("a", false, 100)
        assertEquals(2L, l.remaining("a", false, 101))
        assertEquals(0L, l.remaining("a", false, 103))
        for (t in listOf(160L, 163L, 166L, 169L)) l.record("a", false, t)
        assertEquals(3L, l.remaining("a", false, 169))
    }
}
