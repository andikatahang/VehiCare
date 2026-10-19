package com.vehicare.domain

/**
 * The six preset service types, in the D-23 locked order.
 *
 * These are **code constants, not string resources**: the stored value and the displayed label are
 * the same token (D-40). A stored service type is a stable plain string — there is no rename
 * operation, no type registry, and a value that matches a preset case-insensitively is never
 * rewritten in the database.
 *
 * The vocabulary is **app-wide, not vehicle-scoped** (D-25): a custom label learned on one vehicle
 * is suggested on every vehicle.
 */
object ServiceTypePresets {

    /** The six preset labels, in the locked display order (D-23). */
    val ALL: List<String> = listOf(
        "Oil change",
        "Tire rotation",
        "Brake service",
        "Inspection",
        "Battery",
        "Filters",
    )

    /**
     * The learned custom types from [types] (already newest-used first from SQL), minus any entry
     * that equals a preset with [String.equals] `ignoreCase = true`.
     *
     * A custom label that matches a preset case-insensitively collapses onto that preset's chip —
     * it is filtered out here and **never rewritten** in storage (D-40). Input order is preserved.
     */
    fun filterLearned(types: List<String>): List<String> =
        types.filterNot { name -> ALL.any { preset -> preset.equals(name, ignoreCase = true) } }
}
