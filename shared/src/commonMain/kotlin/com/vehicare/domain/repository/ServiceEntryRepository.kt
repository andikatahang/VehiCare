package com.vehicare.domain.repository

import com.vehicare.domain.HistorySort
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServiceSummary
import kotlinx.coroutines.flow.Flow

/**
 * The only service-log storage contract the UI layer may use.
 *
 * No generated SQLDelight type appears in these signatures on purpose: consumers observe domain
 * models and never touch a driver, a query, or a row type.
 */
interface ServiceEntryRepository {

    /**
     * The vehicle's full service history in the requested [sort] order (D-33/D-34). Both orders
     * are deterministic: equal keys break on `id DESC`. Emits again whenever the tables change.
     */
    fun observeHistory(vehicleId: Long, sort: HistorySort): Flow<List<ServiceEntry>>

    /**
     * The entry with [id] composed with its parts and photo paths, or `null` once it does not
     * exist. Emits on every change to any of the three tables.
     */
    fun observeEntry(id: Long): Flow<ServiceEntry?>

    /**
     * The entry count and last service date for [vehicleId] from one aggregate query — the
     * vehicle Detail summary row never triggers an N+1 read (PITFALLS SQL 3).
     */
    fun observeSummary(vehicleId: Long): Flow<ServiceSummary>

    /**
     * Distinct learned custom service types, newest-used first, shared across all vehicles
     * (D-24/D-25) and already filtered for case-insensitive preset matches (D-40).
     */
    fun observeLearnedTypes(): Flow<List<String>>

    /**
     * The mileage of the most recent entry for [vehicleId] (by service date, `id DESC` tie-break)
     * — the pre-fill source for a new entry (D-28). `null` when the vehicle has no entries.
     */
    fun observeLatestMileage(vehicleId: Long): Flow<Long?>

    /**
     * The mileage of the entry immediately previous to [entryId] in the Date order, excluding
     * [entryId] itself — the edit-mode baseline for the lower-mileage warning (D-28).
     * `null` when [entryId] is the first entry.
     */
    fun observePreviousMileage(vehicleId: Long, entryId: Long): Flow<Long?>

    /**
     * Persists [entry]: an insert when its id is `0L`, an in-place update otherwise. On edit the
     * part, photo and service-type rows are fully replaced, so no orphan children survive
     * (PITFALLS SQL 4).
     *
     * @return the persisted id — the freshly generated one for inserts.
     */
    suspend fun save(entry: ServiceEntry): Long

    /**
     * Deletes the entry with [id], its part and photo rows, and then its photo files — files only
     * after the rows are durably gone (D-07, Shared Pattern 4).
     */
    suspend fun delete(id: Long)
}
