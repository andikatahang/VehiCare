package com.vehicare.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.HistorySort
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServiceSummary
import com.vehicare.domain.ServiceTypePresets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [ServiceEntryRepository] with every database access serialized onto one lane.
 *
 * Doing all I/O on `Dispatchers.IO.limitedParallelism(1)` is the documented guard against
 * concurrent `NativeSqliteDriver` access aborting the iOS process (T-02-01-04); the lane is
 * injected rather than referenced inline so it stays single and testable. Reads ride the lane too,
 * not just writes.
 *
 * @param photoDeleter the seam for removing a deleted entry's photo files. Plan 02-03's DI change
 *   wires the real `PhotoStorage::delete`; a `delete()` that only removed rows would leave orphaned
 *   personal data on disk (D-07).
 */
internal class ServiceEntryRepositoryImpl(
    private val database: VehiCareDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val photoDeleter: suspend (String?) -> Unit = {},
) : ServiceEntryRepository {

    override fun observeHistory(vehicleId: Long, sort: HistorySort): Flow<List<ServiceEntry>> {
        val query = when (sort) {
            HistorySort.DATE -> database.serviceEntryQueries.selectForVehicleByDate(vehicleId)
            HistorySort.MILEAGE -> database.serviceEntryQueries.selectForVehicleByMileage(vehicleId)
        }
        return query.asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeEntry(id: Long): Flow<ServiceEntry?> {
        val entry = database.serviceEntryQueries.selectById(id)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
        val parts = database.serviceEntryQueries.selectParts(id)
            .asFlow()
            .mapToList(ioDispatcher)
        val photos = database.serviceEntryQueries.selectPhotos(id)
            .asFlow()
            .mapToList(ioDispatcher)
        return combine(entry, parts, photos) { row, partRows, photoRows ->
            row?.toDomain()?.copy(
                parts = partRows.map { it.toDomain() },
                photoPaths = photoRows.map { it.toDomain() },
            )
        }
    }

    override fun observeSummary(vehicleId: Long): Flow<ServiceSummary> =
        database.serviceEntryQueries.selectSummary(vehicleId)
            .asFlow()
            .mapToOne(ioDispatcher)
            .map { row -> row.toDomain() }

    override fun observeLearnedTypes(): Flow<List<String>> =
        database.serviceEntryQueries.selectLearnedTypes()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> ServiceTypePresets.filterLearned(rows.map { it.toServiceType() }) }

    override fun observeLatestMileage(vehicleId: Long): Flow<Long?> =
        database.serviceEntryQueries.selectLatestMileage(vehicleId)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)

    override fun observePreviousMileage(vehicleId: Long, entryId: Long): Flow<Long?> =
        database.serviceEntryQueries.selectById(entryId)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { row ->
                val dateMs = row?.service_date_epoch_ms ?: return@map null
                withContext(ioDispatcher) {
                    database.serviceEntryQueries
                        .selectPreviousMileage(
                            vehicle_id = vehicleId,
                            id = entryId,
                            service_date_epoch_ms = dateMs,
                            service_date_epoch_ms_ = dateMs,
                            id_ = entryId,
                        )
                        .executeAsOneOrNull()
                }
            }

    override suspend fun save(entry: ServiceEntry): Long = withContext(ioDispatcher) {
        database.transactionWithResult {
            val params = entry.toBindParameters()
            val id = if (entry.id == 0L) {
                database.serviceEntryQueries.insertEntry(
                    vehicle_id = params.vehicleId,
                    service_date_epoch_ms = params.dateEpochMs,
                    mileage_km = params.mileageKm,
                    service_type = params.serviceType,
                    cost_rupiah = params.costRupiah,
                    notes = params.notes,
                    workshop = params.workshop,
                    mechanic = params.mechanic,
                )
                // Same connection, same transaction: no separate connection can race the id.
                // The id is fetched back via last_insert_rowid() — the minSdk 26-safe way.
                database.serviceEntryQueries.lastInsertId().executeAsOne()
            } else {
                database.serviceEntryQueries.updateEntry(
                    service_date_epoch_ms = params.dateEpochMs,
                    mileage_km = params.mileageKm,
                    service_type = params.serviceType,
                    cost_rupiah = params.costRupiah,
                    notes = params.notes,
                    workshop = params.workshop,
                    mechanic = params.mechanic,
                    id = entry.id,
                )
                // Full replace keeps positions simple and leaves no orphan children.
                database.serviceEntryQueries.deletePartsForEntry(entry.id)
                database.serviceEntryQueries.deletePhotosForEntry(entry.id)
                entry.id
            }
            entry.parts.forEachIndexed { index, part ->
                database.serviceEntryQueries.insertPart(
                    entry_id = id,
                    name = part.name,
                    quantity = part.quantity.toLong(),
                    position = index.toLong(),
                )
            }
            entry.photoPaths.forEachIndexed { index, path ->
                database.serviceEntryQueries.insertPhoto(
                    entry_id = id,
                    photo_path = path,
                    position = index.toLong(),
                )
            }
            id
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        val photoPaths = database.transactionWithResult {
            val paths = database.serviceEntryQueries.selectPhotos(id)
                .executeAsList()
                .map { it.photo_path }
            database.serviceEntryQueries.deletePartsForEntry(id)
            database.serviceEntryQueries.deletePhotosForEntry(id)
            database.serviceEntryQueries.deleteEntry(id)
            paths
        }
        // Files only after the rows are durably gone (D-07, Shared Pattern 4).
        photoPaths.forEach { path -> photoDeleter(path) }
    }
}
