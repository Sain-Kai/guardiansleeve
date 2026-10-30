package com.guardiansleeve.app.data

import com.guardiansleeve.app.model.EmergencyContact
import kotlin.math.*

class ContactsRepository {

    // Pre-configured contacts for user "Sohan"
    // 3 Fixed primary contacts + pool of dynamic contacts
    private val contactsList = mutableListOf(
        // 3 Fixed Contacts (e.g. Family / Nearest Guardian / Police)
        EmergencyContact(
            id = "c1",
            name = "Primary Guardian",
            phone = "+10000000000",
            telegramChatId = "YOUR_PRIMARY_CHAT_ID",
            isFixed = true,
            relationship = "Parent / Guardian"
        ),
        EmergencyContact(
            id = "c2",
            name = "Dad (Primary)",
            phone = "+919876543211",
            telegramChatId = "987654321",
            isFixed = true,
            relationship = "Parent"
        ),
        EmergencyContact(
            id = "c3",
            name = "Emergency Helpline / Police",
            phone = "112",
            telegramChatId = "112233445",
            isFixed = true,
            relationship = "Authority"
        ),

        // Dynamic Contacts with pre-registered home/work geolocations
        EmergencyContact(
            id = "d1",
            name = "Aarav (Colleague)",
            phone = "+919876543212",
            telegramChatId = "554433221",
            latitude = 28.6139,
            longitude = 77.2090,
            isFixed = false,
            relationship = "Friend"
        ),
        EmergencyContact(
            id = "d2",
            name = "Rhea (Flatmate)",
            phone = "+919876543213",
            telegramChatId = "667788990",
            latitude = 28.6200,
            longitude = 77.2150,
            isFixed = false,
            relationship = "Flatmate"
        ),
        EmergencyContact(
            id = "d3",
            name = "Kavita (Gym Buddy)",
            phone = "+919876543214",
            telegramChatId = "778899001",
            latitude = 28.5355,
            longitude = 77.3910,
            isFixed = false,
            relationship = "Friend"
        ),
        EmergencyContact(
            id = "d4",
            name = "Vikram (Neighbor)",
            phone = "+919876543215",
            telegramChatId = "889900112",
            latitude = 28.6100,
            longitude = 77.2000,
            isFixed = false,
            relationship = "Neighbor"
        )
    )

    fun getAllContacts(): List<EmergencyContact> = contactsList.toList()

    fun addContact(contact: EmergencyContact) {
        contactsList.add(contact)
    }

    /**
     * Resolves the top 5 emergency contacts:
     * - 3 Fixed primary contacts
     * - 2 Closest dynamic contacts calculated via Haversine distance from current device location
     */
    fun resolveTop5EmergencyRecipients(currentLat: Double?, currentLng: Double?): List<EmergencyContact> {
        val fixedContacts = contactsList.filter { it.isFixed }.take(3)
        val dynamicCandidates = contactsList.filter { !it.isFixed }

        val dynamicSelected = if (currentLat != null && currentLng != null) {
            dynamicCandidates.map { contact ->
                val dist = if (contact.latitude != null && contact.longitude != null) {
                    calculateHaversineDistanceKm(currentLat, currentLng, contact.latitude, contact.longitude)
                } else {
                    Double.MAX_VALUE
                }
                contact.copy(distanceInKm = dist)
            }.sortedBy { it.distanceInKm ?: Double.MAX_VALUE }
             .take(2)
        } else {
            dynamicCandidates.take(2)
        }

        val resolved = mutableListOf<EmergencyContact>()
        resolved.addAll(fixedContacts.map { it.copy(isSelectedForDispatch = true) })
        resolved.addAll(dynamicSelected.map { it.copy(isSelectedForDispatch = true) })

        return resolved.take(5)
    }

    /**
     * Haversine formula to compute distance between two GPS coordinates in Kilometers
     */
    fun calculateHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }
}
