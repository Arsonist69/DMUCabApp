package com.example.mydmucabapp_passenger.model.DataHandling

import android.util.Log
import com.example.mydmucabapp_passenger.model.DataClass.RideRequest
import com.example.mydmucabapp_passenger.model.DataClass.Trips
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await

class TripsRepository {
    private val db = FirebaseFirestore.getInstance()



    fun storeImmediateTripsInFirestore(trip: Trips, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {

        val tripMap = hashMapOf<String, Any>(
            "passengerId" to trip.passengerId,
            "startLocation" to trip.startLocation,
            "endLocation" to trip.endLocation,
            "route" to trip.route,
            "driverId" to trip.driverId,
            "requestStatus" to "requested"
        )

        trip.scheduledTime?.let {
            tripMap["scheduledTime"] = it
        } ?: run {
            tripMap["scheduledTime"] = Timestamp.now()
        }

        db.collection("immediateRequestedTrips").add(tripMap)
            .addOnSuccessListener { documentReference ->
                val newDocumentId = documentReference.id
                println("DocumentSnapshot added with ID: $newDocumentId")
                onSuccess(newDocumentId)
            }
            .addOnFailureListener { e ->
                println("Error adding document: $e")
                onFailure(e)
            }
    }


    fun deleteRideRequest(documentId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        if (documentId.isEmpty()) {
            onFailure(IllegalArgumentException("Invalid documentId"))
            return
        }

        db.collection("immediateRequestedTrips").document(documentId)
            .delete()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    fun updateRideRequest(documentId: String, trip: Trips, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val tripMap: Map<String, Any> = hashMapOf(
            "driverId" to trip.driverId,
            "requestStatus" to trip.requestStatus
        )

        db.collection("immediateRequestedTrips").document(documentId)
            .update(tripMap)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }



    suspend fun fetchImmediatePostedTripsFromDatabase(collectionName: String): List<DocumentSnapshot> {
        val trips = db.collection(collectionName).get().await()
        return trips.documents
    }


    suspend fun checkDriverAvailableSeats(documentId: String,collectionName: String): Int {
        return try {
            val document = db.collection(collectionName).document(documentId).get().await()
            val availableSeats = document.get("availableSeats")!!.toString().toLong() ?: 0L
            availableSeats.toInt()
        } catch (e: Exception) {
            Log.e("checkSeats", "Error checking available seats for driver $documentId", e)
            0
        }
    }


    interface RideRequestStatusListener {
        fun onStatusChange(status: String)
        fun onError(e: Exception)
    }

    fun listenForRideRequestStatus(documentId: String, listener: RideRequestStatusListener) {
        db.collection("immediateRequestedTrips").document(documentId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    listener.onError(e)
                    return@addSnapshotListener
                }

                snapshot?.let {
                    val status = it.getString("requestStatus") ?: ""
                    listener.onStatusChange(status)
                }
            }
    }



    interface TripDataChangeListener {
        fun onTripAdded(trip: RideRequest)
        fun onTripRemoved(tripId: String)
        fun onTripModified(trip: RideRequest)
        fun onError(e: Exception)
    }

    fun listenForTripChanges(listener: TripDataChangeListener) {

        db.collection("immediatePostedTrips").addSnapshotListener { snapshots, e ->
            if (e != null) {
                listener.onError(e)
                return@addSnapshotListener
            }

            snapshots?.documentChanges?.forEach { docChange ->
                when (docChange.type) {
                    DocumentChange.Type.REMOVED -> {
                        val deletedTripId = docChange.document.id
                        listener.onTripRemoved(deletedTripId)
                    }


                    else -> {}
                }
            }
        }
    }

    fun listenForPassengerTripCompletion(passengerId: String, onDeletion: (Boolean) -> Unit) {
        db.collection("immediateRequestedTrips")
            .whereEqualTo("passengerId", passengerId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("listenForPassengerTripsDeletion", "Listening failed.", e)
                    onDeletion(false)
                    return@addSnapshotListener
                }

                if (snapshots != null && snapshots.isEmpty) {
                    Log.d("listenForPassengerTripsDeletion", "All trips for passenger $passengerId have been deleted or there are no active trips.")
                    onDeletion(true)
                } else {
                    onDeletion(false)
                }
            }
    }



}