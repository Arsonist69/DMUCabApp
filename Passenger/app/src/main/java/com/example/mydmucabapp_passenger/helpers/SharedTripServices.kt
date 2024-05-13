package com.example.mydmucabapp_passenger.helpers

import com.example.mydmucabapp_passenger.model.DataClass.RideRequest
import com.example.mydmucabapp_passenger.model.DataClass.Trips
import com.google.firebase.firestore.FirebaseFirestore

object SharedTripService {
    private var tripDetails: Trips? = null
    private var rideDetails: RideRequest? = null
    private var documentId: String = ""
    private var driverId : String = ""



    fun setTripDetails(trip: Trips) {
        tripDetails = trip
    }

    fun getTripDetails(): Trips? = tripDetails

    fun clearTripDetails() {
        tripDetails = null
    }

    fun setDocumentId(Id : String){
        documentId = Id;
    }
        fun getDocumentId() : String = documentId

    fun clearDocumentId() {
        documentId = ""
    }


    fun setDriverId(Id : String){
        driverId = Id;
    }
    fun getDriverId() : String = driverId

    fun clearDriverId() {
        driverId = ""
    }


    fun setRideDetails(ride: RideRequest) {
        rideDetails = ride
    }

    fun getRideDetails(): RideRequest? = rideDetails

    fun clearRideDetails() {
        rideDetails = null
    }



}




