package com.vehicare.data

import com.vehicare.domain.Vehicle
import kotlinx.coroutines.flow.Flow

/**
 * The only storage contract the UI layer may use.
 *
 * No generated SQLDelight type appears in these signatures on purpose: consumers observe domain
 * models and never touch a driver, a query, or a row type.
 */
interface VehicleRepository {

    /** All vehicles, newest first (D-16). Emits again whenever the table changes. */
    fun observeVehicles(): Flow<List<Vehicle>>

    /** The vehicle with [id], or `null` once it does not exist. Emits on every change. */
    fun observeVehicle(id: Long): Flow<Vehicle?>

    /**
     * Persists [vehicle]: an insert when its id is `0L`, an in-place update otherwise.
     *
     * @return the persisted id — the freshly generated one for inserts.
     */
    suspend fun save(vehicle: Vehicle): Long

    /** Deletes the row with [id] and its photo file (D-07). */
    suspend fun delete(id: Long)
}
