package com.vehicare.util

import androidx.compose.runtime.Composable
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderRule
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.reminder_due_in_km
import vehicare.shared.generated.resources.reminder_due_now
import vehicare.shared.generated.resources.reminder_due_on
import vehicare.shared.generated.resources.reminder_due_soon_days
import vehicare.shared.generated.resources.reminder_due_soon_km
import vehicare.shared.generated.resources.reminder_interval_both
import vehicare.shared.generated.resources.reminder_interval_km
import vehicare.shared.generated.resources.reminder_interval_months
import vehicare.shared.generated.resources.reminder_months_phrase
import vehicare.shared.generated.resources.reminder_no_mileage_data
import vehicare.shared.generated.resources.reminder_overdue_days
import vehicare.shared.generated.resources.reminder_overdue_km
import vehicare.shared.generated.resources.reminder_turned_off

/**
 * The **only** place a due state or a rule's interval becomes text (C-28/C-40).
 *
 * Every Phase 3 surface that says when something is due — the home card's status line, the rule
 * row, the Detail summary row and both platforms' notification bodies — resolves its sentence here.
 * If any of them built its own wording they would eventually disagree, and a reminder that
 * contradicts itself is indistinguishable from a broken reminder. So no other function may build
 * these sentences: no concatenation of numbers, no hand-appended unit, no second date formatter and
 * no delivery-time wording ("at", "exactly", "instantly") anywhere — D-52 forbids promising
 * punctuality.
 *
 * **Three layers, deliberately separated.**
 *  1. The **pure decisions** — [dueSpec] and [intervalSpec] choose *which* sentence a state or a
 *     rule gets, as a [SentenceSpec]/[IntervalSpec]. They touch no resource container, so the
 *     choice itself is assertable in a plain unit test (and in `commonTest` there is no
 *     `ResourceContainer` to load).
 *  2. The **suspend resolvers** — [dueSentence], [intervalSentence], [statusSentence] — for the
 *     notification worker and any non-composition caller (`getString`/`getPluralString` are
 *     `suspend`).
 *  3. The **composable counterparts** — [dueSentenceText], [intervalSentenceText],
 *     [statusSentenceText] — for screens, mirroring layer 2 through `stringResource`.
 *
 * Layers 2 and 3 contain the only duplicated lines in this file and must stay mechanically
 * identical; every branch that decides *which* sentence exists lives exactly once, in the pure
 * layer.
 */
object ReminderCopy {

    /**
     * Which resource a due state resolves to, and with which already-formatted arguments.
     *
     * Numbers are formatted here through [RupiahFormat.formatKmValue] and
     * [ServiceDates.display] — the app's one grouping helper and its one date format — so no
     * surface can re-format a figure differently.
     */
    sealed interface SentenceSpec {

        /** A non-plural string with its format arguments (distances are never pluralized). */
        data class Plain(
            val resource: StringResource,
            val args: List<Any> = emptyList(),
        ) : SentenceSpec

        /** A plural string; [quantity] selects the form and [args] fills its placeholders. */
        data class Plural(
            val resource: PluralStringResource,
            val quantity: Int,
            val args: List<Any> = emptyList(),
        ) : SentenceSpec
    }

    /** Which interval sentence a rule gets — one sentence for both axes ("whichever is first"). */
    sealed interface IntervalSpec {
        data class Km(val km: Long) : IntervalSpec
        data class Months(val months: Long) : IntervalSpec
        data class Both(val km: Long, val months: Long) : IntervalSpec
    }

    /**
     * The one decision mapping a [DueState] to its sentence (C-28).
     *
     * `DueNow` always resolves to the worded `Due now` — never to a zero-count plural, because
     * "0 days left" would describe the moment the interval is reached as if it were still ahead.
     * `NoMileageData` always resolves to `No mileage data yet`: an unevaluable distance axis says
     * so rather than inventing an odometer value (D-49).
     */
    fun dueSpec(state: DueState): SentenceSpec = when (state) {
        is DueState.Upcoming -> state.kmLeft?.let { kmLeft ->
            SentenceSpec.Plain(
                resource = Res.string.reminder_due_in_km,
                args = listOf(RupiahFormat.formatKmValue(kmLeft)),
            )
        } ?: SentenceSpec.Plain(
            resource = Res.string.reminder_due_on,
            args = listOf(
                ServiceDates.display(
                    requireNotNull(state.dueDate) {
                        "Upcoming needs one payload: kmLeft or dueDate (C-28)."
                    },
                ),
            ),
        )

        is DueState.DueSoon -> state.kmLeft?.let { kmLeft ->
            SentenceSpec.Plain(
                resource = Res.string.reminder_due_soon_km,
                args = listOf(RupiahFormat.formatKmValue(kmLeft)),
            )
        } ?: state.daysLeft?.let { daysLeft ->
            val days = daysLeft.toInt()
            SentenceSpec.Plural(
                resource = Res.plurals.reminder_due_soon_days,
                quantity = days,
                args = listOf(days),
            )
        } ?: error("DueSoon needs one payload: kmLeft or daysLeft (C-28).")

        DueState.DueNow -> SentenceSpec.Plain(resource = Res.string.reminder_due_now)

        is DueState.Overdue -> state.kmOver?.let { kmOver ->
            SentenceSpec.Plain(
                resource = Res.string.reminder_overdue_km,
                args = listOf(RupiahFormat.formatKmValue(kmOver)),
            )
        } ?: state.daysOver?.let { daysOver ->
            val days = daysOver.toInt()
            SentenceSpec.Plural(
                resource = Res.plurals.reminder_overdue_days,
                quantity = days,
                args = listOf(days),
            )
        } ?: error("Overdue needs one payload: kmOver or daysOver (C-28).")

        DueState.NoMileageData -> SentenceSpec.Plain(resource = Res.string.reminder_no_mileage_data)
    }

    /**
     * The one decision mapping a rule's intervals to its sentence (C-42).
     *
     * Both axes on one rule read as a single sentence — "Every 5.000 km or 6 months" — because
     * "whichever comes first" is the user's mental model, not two separate reminders (D-46).
     *
     * @throws IllegalStateException when a rule carries neither interval. The editor's C-35 gate
     *   makes that impossible, so a stored row like it is corrupt data and must fail loudly rather
     *   than render an empty line.
     */
    fun intervalSpec(rule: ReminderRule): IntervalSpec {
        val km = rule.intervalKm
        val months = rule.intervalMonths
        return when {
            km != null && months != null -> IntervalSpec.Both(km = km, months = months)
            km != null -> IntervalSpec.Km(km = km)
            months != null -> IntervalSpec.Months(months = months)
            else -> error(
                "A rule needs at least one interval (D-46/C-35): rule ${rule.id} has neither km " +
                    "nor months, so it has no interval sentence.",
            )
        }
    }

    /** The due-state sentence for [state]. The worker and every non-composition caller use this. */
    suspend fun dueSentence(state: DueState): String = resolve(dueSpec(state))

    /** The interval sentence for [rule] — the rule row's always-present second line. */
    suspend fun intervalSentence(rule: ReminderRule): String =
        when (val spec = intervalSpec(rule)) {
            is IntervalSpec.Km -> getString(
                Res.string.reminder_interval_km,
                RupiahFormat.formatKmValue(spec.km),
            )

            is IntervalSpec.Months -> getString(
                Res.string.reminder_interval_months,
                monthsPhrase(spec.months),
            )

            is IntervalSpec.Both -> getString(
                Res.string.reminder_interval_both,
                RupiahFormat.formatKmValue(spec.km),
                monthsPhrase(spec.months),
            )
        }

    /**
     * The status sentence every row shows: `Turned off` when the rule is disabled or has no
     * evaluation, the due-state sentence otherwise.
     *
     * This is where "a disabled rule never renders a due state" (D-43/C-28) is enforced for every
     * surface at once, so no screen has to remember it.
     */
    suspend fun statusSentence(rule: ReminderRule, state: DueState?): String =
        if (!rule.enabled || state == null) {
            getString(Res.string.reminder_turned_off)
        } else {
            dueSentence(state)
        }

    /** The composable twin of [dueSentence]. */
    @Composable
    fun dueSentenceText(state: DueState): String = resolveText(dueSpec(state))

    /** The composable twin of [intervalSentence]. */
    @Composable
    fun intervalSentenceText(rule: ReminderRule): String =
        when (val spec = intervalSpec(rule)) {
            is IntervalSpec.Km -> stringResource(
                Res.string.reminder_interval_km,
                RupiahFormat.formatKmValue(spec.km),
            )

            is IntervalSpec.Months -> stringResource(
                Res.string.reminder_interval_months,
                monthsPhraseText(spec.months),
            )

            is IntervalSpec.Both -> stringResource(
                Res.string.reminder_interval_both,
                RupiahFormat.formatKmValue(spec.km),
                monthsPhraseText(spec.months),
            )
        }

    /** The composable twin of [statusSentence]. */
    @Composable
    fun statusSentenceText(rule: ReminderRule, state: DueState?): String =
        if (!rule.enabled || state == null) {
            stringResource(Res.string.reminder_turned_off)
        } else {
            dueSentenceText(state)
        }

    /** The single resolver for layer 2: exactly two branches, one per [SentenceSpec] shape. */
    private suspend fun resolve(spec: SentenceSpec): String = when (spec) {
        is SentenceSpec.Plain -> getString(spec.resource, *spec.args.toTypedArray())
        is SentenceSpec.Plural -> getPluralString(
            spec.resource,
            spec.quantity,
            *spec.args.toTypedArray(),
        )
    }

    /** The single resolver for layer 3 — mechanically identical to [resolve]. */
    @Composable
    private fun resolveText(spec: SentenceSpec): String = when (spec) {
        is SentenceSpec.Plain -> stringResource(spec.resource, *spec.args.toTypedArray())
        is SentenceSpec.Plural -> pluralStringResource(
            spec.resource,
            spec.quantity,
            *spec.args.toTypedArray(),
        )
    }

    /** "6 months" — the month phrase the interval sentence embeds, never a bare number. */
    private suspend fun monthsPhrase(months: Long): String =
        getPluralString(Res.plurals.reminder_months_phrase, months.toInt(), months.toInt())

    /** The composable twin of [monthsPhrase]. */
    @Composable
    private fun monthsPhraseText(months: Long): String =
        pluralStringResource(Res.plurals.reminder_months_phrase, months.toInt(), months.toInt())
}
