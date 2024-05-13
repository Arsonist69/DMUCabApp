package com.example.mydmucabapp_passenger.model.DataClass

import java.io.Serializable

data class RideRequest(
    val trip: Trips,
    val user: UserModel,
    val documentId: String = "",

    ) : Serializable
