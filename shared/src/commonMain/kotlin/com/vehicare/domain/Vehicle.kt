package com.vehicare.domain

/**
 * A vehicle as the app understands it.
 *
 * This is the only shape the UI layer ever sees: SQLDelight's generated row types stop at the
 * data layer's mapper (see `com.vehicare.data.mapper.VehicleEntityMapper`), so a schema change cannot
 * ripple into a composable.
 *
 * Only [name] is required (D-09) — logging a car must be fast, every other field is optional and
 * can be filled in later from the Edit screen. `id == 0L` means "not persisted yet"; the
 * repository treats a save of such a vehicle as an insert.
 */
data class Vehicle(
    val id: Long = 0L,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val year: String? = null,
    val plate: String? = null,
    val type: VehicleType = VehicleType.Car,
    val photoPath: String? = null,
)
