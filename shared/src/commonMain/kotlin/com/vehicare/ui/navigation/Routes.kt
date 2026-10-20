package com.vehicare.ui.navigation

import kotlinx.serialization.Serializable

/**
 * The phase's four destinations, shared by both platforms (UI-SPEC screens inventory: routes are
 * the RESEARCH Pattern 6 types — reuse them, never invent new ones).
 *
 * `VehicleEditRoute(id = 0L)` is the "add" case and `id > 0` the "edit" case — one destination
 * satisfies D-08's single-form rule without a second screen.
 */
@Serializable
data object VehicleListRoute

/** The Settings tab — the D-17 placeholder slot later phases fill. */
@Serializable
data object SettingsRoute

/** The shared Add/Edit form. `id == 0L` means "add"; any `id > 0` means "edit [id]". */
@Serializable
data class VehicleEditRoute(val id: Long = 0L)

/** One vehicle's Detail screen. */
@Serializable
data class VehicleDetailRoute(val id: Long)

/** The shared Add/Edit service entry form. `entryId == 0L` means "add"; any `entryId > 0` means "edit [entryId]". */
@Serializable
data class ServiceEntryEditRoute(val vehicleId: Long, val entryId: Long = 0L)

/** The read-only Service entry detail screen opened from the history row (D-35). */
@Serializable
data class ServiceEntryDetailRoute(val entryId: Long)
