package com.vehicare.domain

/**
 * The fixed set of vehicle types offered by the type picker (D-10).
 *
 * The database stores the type as a plain string, so a future release can add a type without a
 * migration — and [fromDb] degrades unknown strings to [Car] instead of throwing, so a value
 * written by that future release cannot crash an older build (T-01-19).
 */
enum class VehicleType {
    Car,
    Motorcycle,
    Truck;

    /** The value persisted in `vehicle.type`. */
    fun toDb(): String = name

    companion object {
        /** Parses a persisted `vehicle.type` value; anything unrecognised falls back to [Car]. */
        fun fromDb(value: String): VehicleType = entries.firstOrNull { it.name == value } ?: Car
    }
}
