package com.vehicare.util

/**
 * The one payload a reminder notification carries, and the one parser for it.
 *
 * A scheduled notification has to remember which vehicle it belongs to so a tap can open that
 * vehicle's Detail screen. The payload is a plain `String` — `"v<vehicleId>:r<ruleId>"` — because a
 * string crosses both platforms unchanged: Android's `Intent` extras and iOS's notification
 * `userInfo` would otherwise need `NSDictionary`/`NSNumber` bridging, and a boxed number is exactly
 * where a type or a sign gets lost (RESEARCH U7).
 *
 * **Two callers, one format.** Scheduling encodes it; cancelling re-encodes the *same* identifier
 * from the same ids so the cancellation always targets the posted notification. A rule id is part
 * of the payload because one vehicle can own several reminders and each must be cancellable on its
 * own.
 *
 * **Parsing is strict on purpose.** [vehicleIdOf] accepts only the exact shape this object writes:
 * a leading `v`, exactly one `:r` separator, digit-only halves that each parse to a value `>= 1`,
 * and nothing trailing. Anything else returns `null` rather than a best-effort guess, so a
 * malformed or hostile identifier can never navigate to a wrong — or negative — vehicle id
 * (T-03-14, D-55). Callers treat `null` as "no navigation".
 */
object ReminderNotificationId {

    /** Prefix that marks the payload as a vehicle id, so an unrelated string is rejected early. */
    private const val VEHICLE_PREFIX = "v"

    /** The single separator between the vehicle and rule halves. */
    private const val RULE_SEPARATOR = ":r"

    /** `encode(3, 7)` → `"v3:r7"` — the payload both platforms schedule and cancel with. */
    fun encode(vehicleId: Long, ruleId: Long): String =
        "$VEHICLE_PREFIX$vehicleId$RULE_SEPARATOR$ruleId"

    /**
     * The vehicle id inside [identifier], or `null` when it is not exactly one this object wrote.
     *
     * A `null` result must produce no navigation: a tap can open a vehicle that exists, or nothing
     * at all — never a guessed id.
     */
    fun vehicleIdOf(identifier: String): Long? {
        if (!identifier.startsWith(VEHICLE_PREFIX)) return null

        val separatorIndex = identifier.indexOf(RULE_SEPARATOR)
        if (separatorIndex < 0) return null
        // Exactly one separator: a second one would leave `:r` inside the rule half.
        val nextSeparatorIndex = identifier.indexOf(
            RULE_SEPARATOR,
            startIndex = separatorIndex + RULE_SEPARATOR.length,
        )
        if (nextSeparatorIndex >= 0) return null

        val vehiclePart = identifier.substring(VEHICLE_PREFIX.length, separatorIndex)
        val rulePart = identifier.substring(separatorIndex + RULE_SEPARATOR.length)
        val vehicleId = parsePositiveId(vehiclePart) ?: return null
        parsePositiveId(rulePart) ?: return null
        return vehicleId
    }

    /** A non-empty, digit-only, in-range, `>= 1` id — the only shape [encode] can produce. */
    private fun parsePositiveId(part: String): Long? {
        if (part.isEmpty()) return null
        if (!part.all { it in '0'..'9' }) return null
        return part.toLongOrNull()?.takeIf { it >= 1L }
    }
}
