package com.vehicare.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.vehicare.db.VehiCareDatabase
import com.vehicare.data.mapper.toDomain
import com.vehicare.data.mapper.toBindParameters
import com.vehicare.domain.repository.VehicleRepository
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
 * @param onVehicleDeleted the seam for cancelling the notifications of the reminder rules this
 *   delete just removed (D-64). It receives `(vehicleId, cascadedRuleIds)`, invoked after the
 *   transaction commits and after the photo files are gone — a notification for a vehicle that no
 *   longer exists must not survive the delete. Plan 07 binds it in `AppModule` to
 *   `scheduler.cancel(ruleIds)` followed by `NotificationScheduler.evaluateNow()`, the same
 *   delivery entry point `ReminderRepositoryImpl` uses. The default is a no-op so every existing
 *   call site and test still compiles.
 */
internal class VehicleRepositoryImpl(
    private val database: VehiCareDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val photoDeleter: suspend (String?) -> Unit = {},
    private val onVehicleDeleted: suspend (Long, List<Long>) -> Unit = { _, _ -> },
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
        // Collected inside the transaction, before the rows are gone: the cascade leaves nothing to
        // read afterwards, and the notification hook needs the ids to cancel what it removed.
        val reminderRuleIds = mutableListOf<Long>()
        val photoPaths = database.transactionWithResult {
            val vehiclePhoto = database.vehicleQueries.selectById(id).executeAsOneOrNull()?.photo_path
            val servicePhotos = database.serviceEntryQueries
                .selectPhotoPathsForVehicle(id)
                .executeAsList()
            database.serviceEntryQueries.deletePhotosForVehicle(id)   // children first
            database.serviceEntryQueries.deletePartsForVehicle(id)
            database.serviceEntryQueries.deleteTypesForVehicle(id)
            database.serviceEntryQueries.deleteEntriesForVehicle(id)
            // D-64: a vehicle's reminder rules are part of this same cascade (the D-39 extension),
            // never a second delete path. The `ON DELETE CASCADE` clause alone would not remove
            // them: foreign keys are never enabled — there is no `PRAGMA foreign_keys=ON` anywhere
            // in the app — so the explicit delete is what actually removes the rows.
            reminderRuleIds += database.reminderRuleQueries
                .selectForVehicle(id)
                .executeAsList()
                .map { it.id }
            database.reminderRuleQueries.deleteRulesForVehicle(id)
            database.vehicleQueries.delete(id)
            listOfNotNull(vehiclePhoto) + servicePhotos
        }
        photoPaths.forEach { path -> photoDeleter(path) }
        // After the commit and after the files: a delivered or pending notification for a vehicle
        // that no longer exists must not survive the delete (D-64).
        onVehicleDeleted(id, reminderRuleIds)
    }
}
