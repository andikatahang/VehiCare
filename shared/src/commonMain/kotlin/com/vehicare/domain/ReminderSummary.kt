package com.vehicare.domain

/**
 * The vehicle Detail's Reminders summary row content (D-45): the total rule count and the
 * vehicle's most urgent **enabled** rule.
 *
 * [ruleCount] counts **every** rule, including disabled ones, so the row can tell "no rules at
 * all" from "rules exist but every one is off" — the two cases the meta line renders differently.
 * [top] is `null` exactly when no enabled rule exists; otherwise it is the same row
 * [ReminderOrdering.topRule] would hand the Reminders list and the dashboard, so all three
 * surfaces agree on which rule is "the" rule.
 */
data class ReminderSummary(
    val ruleCount: Int,
    val top: ReminderRow?,
)
