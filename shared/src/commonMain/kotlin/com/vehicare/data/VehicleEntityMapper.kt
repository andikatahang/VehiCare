package com.vehicare.data

import com.vehicare.db.Vehicle as VehicleEntity
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleType

/**
 * The one place SQLDelight's generated row type meets the domain model.
 *
 * Every other file in the app is generated-type-free: the UI sees [Vehicle] only, so a schema
 * change cannot ripple past this boundary (STATE Architecture Constraints).
 */
internal fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id,
    name = name,
    make = make,
    model = model,
    year = year,
    plate = plate,
    type = VehicleType.fromDb(type),
    photoPath = photo_path,
)

/**
 * The seven non-id bind values of a vehicle row, in `Vehicle.sq` query order.
 *
 * Absent optional values stay `null` — an empty string would render differently on the Detail
 * screen's em-dash placeholders.
 */
internal class VehicleBindParameters(
    val name: String,
    val make: String?,
    val model: String?,
    val year: String?,
    val plate: String?,
    val type: String,
    val photoPath: String?,
)

internal fun Vehicle.toBindParameters(): VehicleBindParameters = VehicleBindParameters(
    name = name,
    make = make,
    model = model,
    year = year,
    plate = plate,
    type = type.toDb(),
    photoPath = photoPath,
)
