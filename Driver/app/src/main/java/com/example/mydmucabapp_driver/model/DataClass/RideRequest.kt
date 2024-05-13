package com.example.mydmucabapp_driver.model.DataClass

import java.io.Serializable

data class RideRequest(
    val trip: Trips,
    val user: UserModel,
    val documentId: String = "",
    var isProcessed: Boolean = false
) : Serializable
