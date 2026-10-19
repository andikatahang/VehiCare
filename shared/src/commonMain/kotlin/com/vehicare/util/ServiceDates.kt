package com.vehicare.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The app's single date-conversion seam for service dates.
 *
 * This object is the only place in the app that turns a civil date into stored epoch millis or
 * back again: [today], [toStorage], [fromStorage] and [display] live here and nothing else
 * constructs a storage timestamp or a display string from a date. Do not create a second
 * conversion site anywhere.
 *
 * **The zone asymmetry is deliberate** (RESEARCH Pitfall 1):
 * - [today] reads the user's **wall clock** in the device's current zone — "today" must match the
 *   date the user sees on their phone, not UTC's current date.
 * - [toStorage] and [fromStorage] always pin **UTC** start-of-day, so a stored millis value is
 *   independent of the device zone it was written in and reads back as the same civil date even
 *   after travel or a zone change.
 *
 * A single `toString()`/`parse()` round trip therefore does not preserve the device zone — and it
 * must not: the stored value is the date the user picked, not a moment in time. This is the
 * phase's most likely off-by-one, so do not "fix" the asymmetry into symmetry.
 */
object ServiceDates {

    /** The user's current wall-clock date in the device's zone. */
    fun today(): LocalDate =
        Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Pins [day] to UTC start-of-day and returns the epoch millis the database stores. */
    fun toStorage(day: LocalDate): Long =
        day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    /** Reads a stored epoch-millis value back as the UTC civil date it was written as. */
    fun fromStorage(epochMs: Long): LocalDate =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date

    /** "19 Oct 2026" — the app's one display format for service dates (D-33). */
    fun display(day: LocalDate): String =
        "${day.day} ${MonthNames.ENGLISH_ABBREVIATED.names[day.month.number - 1]} ${day.year}"

    /** [display] for a stored epoch-millis value, read back through [fromStorage]. */
    fun display(epochMs: Long): String = display(fromStorage(epochMs))
}
