package com.vehicare.notifications

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [FirstReminderAskSignal] — publish, the non-positive-id guard, replacement, consume
 * and the non-latching replay.
 *
 * The object is process-wide state, so every test resets it in [BeforeTest]/[AfterTest] to keep
 * one test from leaking its pending value into the next.
 */
class FirstReminderAskSignalTest {

    @BeforeTest
    fun setUp() {
        FirstReminderAskSignal.consume()
    }

    @AfterTest
    fun tearDown() {
        FirstReminderAskSignal.consume()
    }

    @Test
    fun publishingAPositiveIdMakesItTheCurrentPending() {
        FirstReminderAskSignal.publish(7L)

        assertEquals(7L, FirstReminderAskSignal.pending.value)
    }

    @Test
    fun publishingZeroLeavesThePendingValueUntouched() {
        FirstReminderAskSignal.publish(7L)

        FirstReminderAskSignal.publish(0L)

        assertEquals(7L, FirstReminderAskSignal.pending.value)
    }

    @Test
    fun publishingANegativeIdLeavesThePendingValueUntouched() {
        FirstReminderAskSignal.publish(7L)

        FirstReminderAskSignal.publish(-1L)

        assertEquals(7L, FirstReminderAskSignal.pending.value)
    }

    @Test
    fun aSecondPublishReplacesTheFirst() {
        FirstReminderAskSignal.publish(7L)

        FirstReminderAskSignal.publish(9L)

        assertEquals(9L, FirstReminderAskSignal.pending.value)
    }

    @Test
    fun consumeClearsThePendingValue() {
        FirstReminderAskSignal.publish(7L)

        FirstReminderAskSignal.consume()

        assertNull(FirstReminderAskSignal.pending.value)
    }

    @Test
    fun publishingTheSameIdAfterConsumeIsObservableAgain() {
        // Proves consume() does not latch the bus into an "already seen" state — the same id can
        // raise a fresh ask on a later add.
        FirstReminderAskSignal.publish(7L)
        FirstReminderAskSignal.consume()

        FirstReminderAskSignal.publish(7L)

        assertEquals(7L, FirstReminderAskSignal.pending.value)
    }
}
