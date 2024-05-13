package com.example.mydmucabapp_driver.helpers

import android.location.Location
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.example.mydmucabapp_driver.model.DataClass.Trips
import com.google.firebase.firestore.FirebaseFirestore

object SharedTripService {
    private var tripDetails: Trips? = null
    private var documentId: String = ""
    private var requestedDocumentIds: MutableList<String> = mutableListOf()
    private var acceptedTripsDocumentIds: MutableList<String> = mutableListOf();
    private val acceptedTrips: MutableList<RideRequest> = mutableListOf()
    private var driverLocationLatitude: Double? = null
    private var driverLocationLongitude: Double? = null
    fun setTripDetails(trip: Trips) {
        tripDetails = trip
    }

    fun getTripDetails(): Trips? = tripDetails

    fun clearTripDetails() {
        tripDetails = null
    }

    fun setPostedTripsDocumentId(Id: String) {
        documentId = Id;
    }

    fun getPostedTripsDocumentId(): String = documentId

    fun clearPostedTripsDocumentId() {
        documentId = ""
    }

    fun setRequestedTripsDocumentIds(ids: List<String>) {
        requestedDocumentIds.addAll(ids.filterNot { it in requestedDocumentIds })
    }


    fun getRequestedTripsDocumentIds(): List<String> = requestedDocumentIds.toList()

    fun clearRequestedTripsDocumentIds() {
        requestedDocumentIds.clear()
    }

    fun setAcceptedTripsDocumentIds(id: String) {
        if (id !in acceptedTripsDocumentIds) {
            acceptedTripsDocumentIds.add(id)
        }
    }

    fun getAcceptedTripsDocumentIds(): List<String> = acceptedTripsDocumentIds.toList()

    fun clearAcceptedTripsDocumentIds() {
        acceptedTripsDocumentIds.clear()
    }

    fun addAcceptedTrip(rideRequest: RideRequest) {
        acceptedTrips.add(rideRequest)
    }

    fun getAcceptedTrips(): List<RideRequest> {
        return acceptedTrips.toList() // Return a copy to prevent external modification
    }

    fun clearAcceptedTrips() {
        acceptedTrips.clear()
    }


    fun setLastKnownLocation(latitude: Double, longitude: Double) {
        driverLocationLatitude = latitude
        driverLocationLongitude = longitude
    }

    fun getLastKnownLocation(): Location? {
        val latitude = driverLocationLatitude
        val longitude = driverLocationLongitude

        return if (latitude != null && longitude != null) {
            Location("driverLocation").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
        } else {
            null
        }
    }

    fun clearLastKnownLocation() {
        driverLocationLatitude = null
        driverLocationLongitude = null
    }

}




