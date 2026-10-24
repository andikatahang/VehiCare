package com.vehicare.data.mapper

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
    // Pre-hydration fallback only: the repository replaces this with the ordered rows of
    // `service_entry_type`, and it keeps the list non-empty for any caller that maps a bare row.
    serviceTypes = listOf(service_type),
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
internal fun SelectLearnedTypes.toServiceType(): String = label

/**
 * The eight non-id bind values of a `service_entry` row, in `ServiceEntry.sq` query order.
 *
 * [serviceTypeMirror] is the position-0 label, written only to the legacy
 * `service_entry.service_type` column; the `service_entry_type` rows are the source of truth.
 */
internal class ServiceEntryBindParameters(
    val vehicleId: Long,
    val dateEpochMs: Long,
    val mileageKm: Long,
    val serviceTypeMirror: String,
    val costRupiah: Long?,
    val notes: String?,
    val workshop: String?,
    val mechanic: String?,
)

internal fun ServiceEntry.toBindParameters(): ServiceEntryBindParameters = ServiceEntryBindParameters(
    vehicleId = vehicleId,
    dateEpochMs = dateEpochMs,
    mileageKm = mileageKm,
    // Non-empty by contract; a violation fails the save transaction rather than writing an
    // empty mirror.
    serviceTypeMirror = serviceTypes.first(),
    costRupiah = costRupiah,
    notes = notes,
    workshop = workshop,
    mechanic = mechanic,
)
