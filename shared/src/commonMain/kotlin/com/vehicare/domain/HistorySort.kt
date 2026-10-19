package com.vehicare.domain

/**
 * The two orders the service history screen offers (D-34).
 *
 * Both directions are fixed descending (newest date / highest mileage). This is a plain enum with
 * no `toDb`/`fromDb`: the choice is view-model state only and is **never persisted** (D-34).
 */
enum class HistorySort {
    DATE,
    MILEAGE,
}
