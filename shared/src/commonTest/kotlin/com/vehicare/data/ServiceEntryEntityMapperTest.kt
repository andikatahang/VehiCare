package com.vehicare.data

import com.vehicare.data.mapper.toBindParameters
import com.vehicare.data.mapper.toDomain
import com.vehicare.data.mapper.toServiceType
import com.vehicare.db.SelectLearnedTypes
import com.vehicare.db.SelectSummary
import com.vehicare.db.Service_entry as ServiceEntryEntity
import com.vehicare.db.Service_part as ServicePartEntity
import com.vehicare.db.Service_photo as ServicePhotoEntity
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServicePart
import com.vehicare.domain.ServiceSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceEntryEntityMapperTest {

    @Test
    fun mapsEveryColumnOneToOne() {
        val entity = ServiceEntryEntity(
            id = 7L,
            vehicle_id = 3L,
            service_date_epoch_ms = 1_768_867_200_000L,
            mileage_km = 123_456L,
            service_type = "Oil change",
            cost_rupiah = 450_000L,
            notes = "Synthetic 5W-30",
            workshop = "AutoPro",
            mechanic = "Budi",
        )

        assertEquals(
            ServiceEntry(
                id = 7L,
                vehicleId = 3L,
                dateEpochMs = 1_768_867_200_000L,
                mileageKm = 123_456L,
                serviceTypes = listOf("Oil change"),
                costRupiah = 450_000L,
                notes = "Synthetic 5W-30",
                workshop = "AutoPro",
                mechanic = "Budi",
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun keepsAbsentOptionalColumnsNull() {
        val entity = ServiceEntryEntity(
            id = 1L,
            vehicle_id = 2L,
            service_date_epoch_ms = 0L,
            mileage_km = 0L,
            service_type = "Custom type",
            cost_rupiah = null,
            notes = null,
            workshop = null,
            mechanic = null,
        )

        val entry = entity.toDomain()

        assertNull(entry.costRupiah)
        assertNull(entry.notes)
        assertNull(entry.workshop)
        assertNull(entry.mechanic)
        assertEquals(emptyList(), entry.parts)
        assertEquals(emptyList(), entry.photoPaths)
    }

    @Test
    fun mapsPartRowsToDomainParts() {
        val entity = ServicePartEntity(
            id = 9L,
            entry_id = 4L,
            name = "Oil filter",
            quantity = 2L,
            position = 1L,
        )

        assertEquals(ServicePart(name = "Oil filter", quantity = 2), entity.toDomain())
    }

    @Test
    fun mapsPhotoRowsToTheirRelativePath() {
        val entity = ServicePhotoEntity(
            id = 5L,
            entry_id = 4L,
            photo_path = "service_entries/4-1768867200000.jpg",
            position = 0L,
        )

        assertEquals("service_entries/4-1768867200000.jpg", entity.toDomain())
    }

    @Test
    fun bindParametersEmitTheNonIdColumnsInQueryOrder() {
        val entry = ServiceEntry(
            id = 12L,
            vehicleId = 3L,
            dateEpochMs = 1_768_867_200_000L,
            mileageKm = 123_456L,
            serviceTypes = listOf("Brake service"),
            costRupiah = 200_000L,
            notes = null,
            workshop = "AutoPro",
            mechanic = null,
        )

        val params = entry.toBindParameters()

        assertEquals(3L, params.vehicleId)
        assertEquals(1_768_867_200_000L, params.dateEpochMs)
        assertEquals(123_456L, params.mileageKm)
        assertEquals("Brake service", params.serviceTypeMirror)
        assertEquals(200_000L, params.costRupiah)
        assertNull(params.notes)
        assertEquals("AutoPro", params.workshop)
        assertNull(params.mechanic)
    }

    @Test
    fun mapsTheSummaryAggregateIncludingTheZeroEntryCase() {
        val zero = SelectSummary(entry_count = 0L, last_service_epoch_ms = null)
        assertEquals(ServiceSummary(entryCount = 0, lastServiceEpochMs = null), zero.toDomain())

        val two = SelectSummary(entry_count = 2L, last_service_epoch_ms = 1_768_867_200_000L)
        assertEquals(ServiceSummary(entryCount = 2, lastServiceEpochMs = 1_768_867_200_000L), two.toDomain())
    }

    @Test
    fun bareRowFallbackKeepsTheTypeNonEmpty() {
        // This is the path used before hydration — and by any read that has no child rows.
        val entity = ServiceEntryEntity(
            id = 4L,
            vehicle_id = 1L,
            service_date_epoch_ms = 0L,
            mileage_km = 0L,
            service_type = "Custom type",
            cost_rupiah = null,
            notes = null,
            workshop = null,
            mechanic = null,
        )

        assertEquals(listOf("Custom type"), entity.toDomain().serviceTypes)
    }

    @Test
    fun mapsLearnedTypeRowsToTheirStoredLabel() {
        val row = SelectLearnedTypes(label = "oil change", last_used_id = 42L)

        assertEquals("oil change", row.toServiceType())
    }
}
