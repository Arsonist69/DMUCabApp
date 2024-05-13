package com.example.mydmucabapp_passenger.model.DataClass

import java.io.Serializable

data class UserModel(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val profileImageUrl: String? = null,
    val roles: Map<String, Boolean> = mapOf("passenger" to true, "driver" to false),
    val driverLicenseCheckCode: String? = null,
    val vehicleRegistration: String? = null,
    val passengerAccountIsActive: Boolean = true,
    val driverAccountIsActive: Boolean = false,
    val insuranceCertificateImageUrl: String? = null,
    val idDocument: String? = null,
    val vehicleColor: String? = null,
    val vehicleModel: String? = null

) : Serializable
