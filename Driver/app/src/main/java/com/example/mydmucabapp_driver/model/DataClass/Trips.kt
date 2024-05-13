package com.example.mydmucabapp_driver.model.DataClass

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.io.Serializable

data class Trips(
    val passengerId: String = "",
    val startLocation: GeoPoint = GeoPoint(0.0, 0.0),
    val endLocation: GeoPoint = GeoPoint(0.0, 0.0),
    val route: String = "",
    val scheduledTime: Timestamp? = null,
    val availableSeats: Int? = null,
    val driverId: String = "",
    val requestStatus: String = "",
    val tripStatus: String = "inactive"
) : Serializable


