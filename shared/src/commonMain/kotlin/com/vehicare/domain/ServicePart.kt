package com.vehicare.domain

/**
 * One part replaced or repaired during a service entry (SVC-02).
 *
 * Both fields are required once a part row exists: a blank name drops the row at save time
 * (parts are optional per D-27), and [quantity] is always at least 1.
 */
data class ServicePart(
    val name: String,
    val quantity: Int = 1,
)
