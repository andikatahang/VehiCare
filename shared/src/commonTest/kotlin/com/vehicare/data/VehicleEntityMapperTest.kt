package com.vehicare.data

import com.vehicare.db.Vehicle as VehicleEntity
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VehicleEntityMapperTest {

    @Test
    fun mapsEveryColumnOneToOne() {
        val entity = VehicleEntity(
            id = 7L,
            name = "Ranger",
            make = "Ford",
            model = "Ranger XLT",
            year = "2020",
            plate = "B 1234 XYZ",
            type = "Truck",
            photo_path = "photos/veh-7.jpg",
        )

        assertEquals(
            Vehicle(
                id = 7L,
                name = "Ranger",
                make = "Ford",
                model = "Ranger XLT",
                year = "2020",
                plate = "B 1234 XYZ",
                type = VehicleType.Truck,
                photoPath = "photos/veh-7.jpg",
            ),
            entity.toDomain(),
        )
    }

    @Test
    fun keepsAbsentOptionalColumnsNull() {
        val entity = VehicleEntity(
            id = 1L,
            name = "A",
            make = null,
            model = null,
            year = null,
            plate = null,
            type = "Car",
            photo_path = null,
        )

        val vehicle = entity.toDomain()

        assertNull(vehicle.make)
        assertNull(vehicle.model)
        assertNull(vehicle.year)
        assertNull(vehicle.plate)
        assertNull(vehicle.photoPath)
    }

    @Test
    fun bindParametersEmitTheEnumDbStringAndKeepThePhotoPath() {
        val vehicle = Vehicle(
            id = 3L,
            name = "B",
            type = VehicleType.Motorcycle,
            photoPath = "photos/b.jpg",
        )

        val params = vehicle.toBindParameters()

        assertEquals("B", params.name)
        assertEquals("Motorcycle", params.type)
        assertEquals("photos/b.jpg", params.photoPath)
        assertNull(params.make)
    }

    @Test
    fun unknownStoredTypeFallsBackToCar() {
        val entity = VehicleEntity(
            id = 9L,
            name = "C",
            make = null,
            model = null,
            year = null,
            plate = null,
            type = "Spaceship",
            photo_path = null,
        )

        assertEquals(VehicleType.Car, entity.toDomain().type)
    }
}
