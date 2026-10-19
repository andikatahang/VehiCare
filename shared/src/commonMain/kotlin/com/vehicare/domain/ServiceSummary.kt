package com.vehicare.domain

/**
 * The aggregate a vehicle's service log is summarised by (D-32).
 *
 * Comes from one SQL aggregate (`COUNT(*)` + `MAX(service_date_epoch_ms)`, PITFALLS SQL 3), so the
 * vehicle Detail summary row never triggers an N+1 read. [lastServiceEpochMs] is `null` exactly
 * when [entryCount] is 0.
 */
data class ServiceSummary(
    val entryCount: Int,
    val lastServiceEpochMs: Long?,
)
