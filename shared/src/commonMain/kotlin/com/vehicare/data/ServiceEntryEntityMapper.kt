package com.vehicare.data

import com.vehicare.db.SelectLearnedTypes
import com.vehicare.db.SelectSummary
import com.vehicare.db.Service_entry as ServiceEntryEntity
import com.vehicare.db.Service_part as ServicePartEntity
import com.vehicare.db.Service_photo as ServicePhotoEntity
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServicePart
import com.vehicare.domain.ServiceSummary

/**
 * The one place SQLDelight's generated row types meet the service-log domain models.
 *
 * Every other file in the app is generated-type-free: the UI sees [ServiceEntry] only, so a schema
 * change cannot ripple past this boundary (STATE Architecture Constraints). The generated row
 * classes are named after the table (`Service_entry`, `Service_part`, `Service_photo`) — the
 * aliases below keep that detail boxed inside this file, exactly as `VehicleEntityMapper` does.
 */
internal fun ServiceEntryEntity.toDomain(): ServiceEntry = ServiceEntry(
    id = id,
    vehicleId = vehicle_id,
    dateEpochMs = service_date_epoch_ms,
    mileageKm = mileage_km,
    serviceType = service_type,
    costRupiah = cost_rupiah,
    notes = notes,
    workshop = workshop,
    mechanic = mechanic,
)

/** Child rows carry no id into the domain — position is their only order. */
internal fun ServicePartEntity.toDomain(): ServicePart = ServicePart(
    name = name,
    quantity = quantity.toInt(),
)

/** Maps a `service_photo` row to the relative path the domain (and `PhotoStorage`) uses (D-05). */
internal fun ServicePhotoEntity.toDomain(): String = photo_path

/**
 * Maps the `selectSummary` aggregate row. `COUNT(*)` is a 64-bit integer in SQLite; the domain
 * exposes it as an [Int] because entry counts cannot plausibly exceed it.
 */
internal fun SelectSummary.toDomain(): ServiceSummary = ServiceSummary(
    entryCount = entry_count.toInt(),
    lastServiceEpochMs = last_service_epoch_ms,
)

/** `selectLearnedTypes` returns the stored label as-is — it is never rewritten (D-40). */
internal fun SelectLearnedTypes.toServiceType(): String = service_type

/** The eight non-id bind values of a `service_entry` row, in `ServiceEntry.sq` query order. */
internal class ServiceEntryBindParameters(
    val vehicleId: Long,
    val dateEpochMs: Long,
    val mileageKm: Long,
    val serviceType: String,
    val costRupiah: Long?,
    val notes: String?,
    val workshop: String?,
    val mechanic: String?,
)

internal fun ServiceEntry.toBindParameters(): ServiceEntryBindParameters = ServiceEntryBindParameters(
    vehicleId = vehicleId,
    dateEpochMs = dateEpochMs,
    mileageKm = mileageKm,
    serviceType = serviceType,
    costRupiah = costRupiah,
    notes = notes,
    workshop = workshop,
    mechanic = mechanic,
)
