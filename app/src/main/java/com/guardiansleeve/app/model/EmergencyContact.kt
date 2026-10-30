package com.guardiansleeve.app.model

/**
 * Emergency contact model.
 * Top 5 emergency contacts:
 *  - 3 Fixed priority contacts (family, trusted guardians)
 *  - 2 Dynamic contacts selected based on closest Haversine distance to current GPS location.
 */
data class EmergencyContact(
    val id: String,
    val name: String,
    val phone: String,
    val telegramChatId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isFixed: Boolean = false,
    val relationship: String = "Guardian",
    var distanceInKm: Double? = null,
    var isSelectedForDispatch: Boolean = false
)
