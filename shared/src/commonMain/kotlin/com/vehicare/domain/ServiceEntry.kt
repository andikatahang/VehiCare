package com.vehicare.domain

/**
 * A logged service event for one vehicle.
 *
 * This is the only shape the UI layer ever sees: SQLDelight's generated row types stop at the
 * data layer's mapper (see `com.vehicare.data.ServiceEntryEntityMapper`).
 *
 * Only [vehicleId], [dateEpochMs], [mileageKm] and [serviceTypes] are required to save an entry
 * (D-27); cost, notes, workshop, mechanic, parts and photos are optional and can be filled in
 * later from the Edit screen. `id == 0L` means "not persisted yet"; the repository treats a save
 * of such an entry as an insert.
 *
 * [serviceTypes] is ordered — position is display order — and must hold at least one label when the
 * entry is saved. Labels are stored verbatim and are never rewritten when two of them collapse
 * case-insensitively into one chip (D-40). The first element is what the legacy
 * `service_entry.service_type` mirror column holds; the child rows are the source of truth.
 *
 * [dateEpochMs] is the **UTC start-of-day** millis of the chosen civil date — never a `now()`
 * timestamp (see `com.vehicare.util.ServiceDates`). [costRupiah] is whole rupiah, never floating
 * point (D-29). [photoPaths] are relative paths through the single `PhotoStorage` seam (D-05/D-37).
 */
data class ServiceEntry(
    val id: Long = 0L,
    val vehicleId: Long,
    val dateEpochMs: Long,
    val mileageKm: Long,
    val serviceTypes: List<String>,
    val costRupiah: Long? = null,
    val notes: String? = null,
    val workshop: String? = null,
    val mechanic: String? = null,
    val parts: List<ServicePart> = emptyList(),
    val photoPaths: List<String> = emptyList(),
)
