package com.vehicare.data.mapper

import com.vehicare.db.Reminder_rule as ReminderRuleEntity
import com.vehicare.db.SelectTypeBaselines
import com.vehicare.db.SelectVehicleStats
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.VehicleStats

/*
 * The one place SQLDelight's generated reminder rows meet the reminder domain models.
 *
 * Same contract as `VehicleEntityMapper` and `ServiceEntryEntityMapper`: every other file in the
 * app is generated-type-free, so a schema change cannot ripple past this boundary (STATE
 * Architecture Constraints). The generated row classes are named after the table
 * (`Reminder_rule`) and after each query (`SelectTypeBaselines`, `SelectVehicleStats`) — the
 * aliases and the signatures below keep those details boxed inside `data/`.
 *
 * The two baseline reads live here rather than in `ServiceEntryEntityMapper` because their only
 * consumer is reminder calculation: a rule measures its interval from one (vehicle, service type)
 * baseline (D-47) or, when its type has no entry yet, from the vehicle's newest entry (D-49).
 * They are derived data — no service-log surface reads them.
 */

/**
 * Maps a `reminder_rule` row.
 *
 * `enabled` is the only place the stored `INTEGER` becomes a `Boolean`: the project ships no
 * `ColumnAdapter`, and SQLDelight maps `INTEGER` to `Long` (see `ReminderRule.sq`).
 *
 * Both bookkeeping markers are passed through by name. Dropping either one would make the delivery
 * plan read every rule as unclaimed and never armed, which is exactly the re-nag D-51 forbids.
 */
internal fun ReminderRuleEntity.toDomain(): ReminderRule = ReminderRule(
    id = id,
    vehicleId = vehicle_id,
    serviceType = service_type,
    intervalKm = interval_km,
    intervalMonths = interval_months,
    leadKm = lead_km,
    leadDays = lead_days,
    enabled = enabled != 0L,
    createdAtEpochMs = created_at_epoch_ms,
    notifiedCycleKey = notified_cycle_key,
    armedCycleKey = armed_cycle_key,
)

/**
 * Maps one `selectTypeBaselines` row — the newest entry of a (vehicle, service type) pair (D-47).
 *
 * Both numbers arrive from the **same** service entry because the SQL selects a bare `mileage_km`
 * beside a single `MAX(service_date_epoch_ms)` (SQLite's single-aggregate bare-column rule), so
 * this mapper never has to pair two reads.
 *
 * **The `MAX(...)` column is generated non-null here** (`baseline_date_epoch_ms: Long`), unlike
 * `selectSummary`'s `last_service_epoch_ms: Long?`: `GROUP BY` guarantees at least one row per
 * group, and SQLDelight 2.4.1 narrows the aggregate accordingly (verified against
 * `build/generated/.../SelectTypeBaselines.kt`). There is therefore no null to filter and — just as
 * importantly — **no `0L` fallback is substituted**: a sentinel would read back as 1 Jan 1970 and
 * make every such rule instantly overdue.
 */
internal fun SelectTypeBaselines.toDomain(): TypeBaseline = TypeBaseline(
    vehicleId = vehicle_id,
    typeLabel = type_label,
    baselineDateEpochMs = baseline_date_epoch_ms,
    baselineKm = baseline_km,
)

/**
 * Maps one `selectVehicleStats` row: the vehicle's newest service date and that same row's
 * odometer (D-49's fallback for a rule whose type has no entry yet).
 *
 * The domain keeps both fields nullable because a vehicle with no entries has no stats row at all;
 * a row that exists always carries both numbers. No odometer is ever invented for the missing case.
 */
internal fun SelectVehicleStats.toDomain(): VehicleStats = VehicleStats(
    vehicleId = vehicle_id,
    lastServiceEpochMs = last_service_epoch_ms,
    lastMileageKm = last_mileage_km,
)
