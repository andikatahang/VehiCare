package com.vehicare.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.Vehicle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [VehicleRepository] with every database access serialized onto one lane.
 *
 * Doing all I/O on `Dispatchers.IO.limitedParallelism(1)` is the documented guard against
 * concurrent `NativeSqliteDriver` access aborting the iOS process (T-01-15); the lane is
 * injected rather than referenced inline so it stays single and testable.
 *
 * @param photoDeleter the seam for removing a deleted vehicle's photo file. Plan 01-08 replaces
 *   the no-op default with `PhotoStorage::delete`; a `delete()` that only removes the row would
 *   leave orphaned personal data on disk (D-07). The D-39 cascade extends this to every service
 *   entry photo file belonging to the vehicle.
 */
internal class VehicleRepositoryImpl(
    private val database: VehiCareDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val photoDeleter: suspend (String?) -> Unit = {},
) : VehicleRepository {

    override fun observeVehicles(): Flow<List<Vehicle>> =
        database.vehicleQueries.selectAll()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeVehicle(id: Long): Flow<Vehicle?> =
        database.vehicleQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { row -> row?.toDomain() }

    override suspend fun save(vehicle: Vehicle): Long = withContext(ioDispatcher) {
        database.transactionWithResult {
            val params = vehicle.toBindParameters()
            if (vehicle.id == 0L) {
                database.vehicleQueries.insert(
                    name = params.name,
                    make = params.make,
                    model = params.model,
                    year = params.year,
                    plate = params.plate,
                    type = params.type,
                    photo_path = params.photoPath,
                )
                // Same connection, same transaction: no separate connection can race the id.
                database.vehicleQueries.lastInsertId().executeAsOne()
            } else {
                database.vehicleQueries.update(
                    name = params.name,
                    make = params.make,
                    model = params.model,
                    year = params.year,
                    plate = params.plate,
                    type = params.type,
                    photo_path = params.photoPath,
                    id = vehicle.id,
                )
                vehicle.id
            }
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        val photoPaths = database.transactionWithResult {
            val vehiclePhoto = database.vehicleQueries.selectById(id).executeAsOneOrNull()?.photo_path
            val servicePhotos = database.serviceEntryQueries
                .selectPhotoPathsForVehicle(id)
                .executeAsList()
            database.serviceEntryQueries.deletePhotosForVehicle(id)   // children first
            database.serviceEntryQueries.deletePartsForVehicle(id)
            database.serviceEntryQueries.deleteTypesForVehicle(id)
            database.serviceEntryQueries.deleteEntriesForVehicle(id)
            database.vehicleQueries.delete(id)
            listOfNotNull(vehiclePhoto) + servicePhotos
        }
        photoPaths.forEach { path -> photoDeleter(path) }
    }
}
