package com.vehicare.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `"v<vehicleId>:r<ruleId>"` payload (RESEARCH U7): it must round-trip exactly, and every
 * malformed shape must parse to `null` so a tap can never navigate to a wrong vehicle (T-03-14).
 */
class ReminderNotificationIdTest {

    @Test
    fun encodeWritesTheDocumentedShape() {
        assertEquals("v3:r7", ReminderNotificationId.encode(vehicleId = 3, ruleId = 7))
        assertEquals("v1:r1", ReminderNotificationId.encode(vehicleId = 1, ruleId = 1))
        assertEquals(
            "v9007199254740993:r9223372036854775807",
            ReminderNotificationId.encode(
                vehicleId = 9_007_199_254_740_993L,
                ruleId = Long.MAX_VALUE,
            ),
        )
    }

    @Test
    fun everyEncodedPairRoundTripsToItsVehicle() {
        val pairs = listOf(
            1L to 1L,
            3L to 7L,
            42L to 9_999L,
            // Beyond Double's exact-integer range: a boxed/JS-bridged number would lose precision.
            9_007_199_254_740_993L to 12L,
            Long.MAX_VALUE to Long.MAX_VALUE,
        )

        for ((vehicleId, ruleId) in pairs) {
            val identifier = ReminderNotificationId.encode(vehicleId, ruleId)
            assertEquals(
                vehicleId,
                ReminderNotificationId.vehicleIdOf(identifier),
                "round-trip failed for $identifier",
            )
        }
    }

    @Test
    fun vehicleIdOfRejectsEveryMalformedShape() {
        val malformed = listOf(
            "",
            "vehicle:3",
            "v3",
            "v3:r",
            "v:r3",
            "v0:r3",
            "v3:r0",
            "v-1:r2",
            "v3:r4x",
            "v3:r4:5",
            " v3:r4",
        )

        for (identifier in malformed) {
            assertNull(
                ReminderNotificationId.vehicleIdOf(identifier),
                "should reject: \"$identifier\"",
            )
        }
    }

    @Test
    fun vehicleIdOfRejectsUppercaseNegativeOverflowingAndTrailingShapes() {
        val malformed = listOf(
            "V3:r4",
            "v3:r-1",
            "v3:r4 ",
            "v3:r4\n",
            "v+3:r4",
            "v99999999999999999999:r4",
        )

        for (identifier in malformed) {
            assertNull(
                ReminderNotificationId.vehicleIdOf(identifier),
                "should reject: \"$identifier\"",
            )
        }
    }
}
