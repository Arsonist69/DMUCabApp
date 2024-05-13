package com.example.mydmucabapp_driver.model.DataHandling

import android.util.Log
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.example.mydmucabapp_driver.model.DataClass.Trips
import com.example.mydmucabapp_driver.model.DataClass.UserModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TripsRepository {

   private val db = FirebaseFirestore.getInstance()

    fun storeImmediateTripsInFirestore(
        trip: Trips,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        val tripMap = hashMapOf<String, Any>(
            "driverId" to trip.driverId,
            "startLocation" to trip.startLocation,
            "endLocation" to trip.endLocation,
            "route" to trip.route,
        )


        trip.availableSeats?.let {
            tripMap["availableSeats"] = it
        }

        trip.scheduledTime?.let {
            tripMap["scheduledTime"] = it
        } ?: run {
            tripMap["scheduledTime"] = Timestamp.now()
        }

        db.collection("immediatePostedTrips").add(tripMap)
            .addOnSuccessListener { documentReference ->

                onSuccess(documentReference.id)
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }


    fun listenForRideRequests(driverId: String, listener: RideRequestListener) {
        val db = FirebaseFirestore.getInstance()
        db.collection("immediateRequestedTrips")
            .whereEqualTo("driverId", driverId)
            .whereEqualTo("requestStatus", "requested")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w("rideRequest", "Listen failed.", error)
                    return@addSnapshotListener
                }

                val documentIds = snapshots?.documents?.map { it.id } ?: listOf()
                listener.onNewRequest(documentIds)
            }
    }

    interface RideRequestListener {
        fun onNewRequest(documentIds: List<String>)
    }


    suspend fun fetchRideRequests(documentIds: List<String>, collectionName: String = "immediateRequestedTrips"): List<RideRequest> =
        withContext(Dispatchers.IO) {
            val rides = mutableListOf<RideRequest>()
            val db = FirebaseFirestore.getInstance()

            documentIds.forEach { documentId ->
                try {
                    val tripSnapshot =
                        db.collection(collectionName).document(documentId).get().await()

                    if (!tripSnapshot.exists()) {
                        Log.w("fetchRides", "No trip found with document ID: $documentId")
                        return@forEach
                    }

                    val trip = tripSnapshot.toObject(Trips::class.java)
                    if (trip == null) {
                        Log.w(
                            "fetchRides",
                            "Failed to parse trip from document: $tripSnapshot.data"
                        )
                        return@forEach
                    }

                    val passengerId = tripSnapshot.getString("passengerId")
                    if (passengerId == null) {
                        Log.w("fetchRides", "No driverId found in trip document: $documentId")
                        return@forEach
                    }

                    val userSnapshot = db.collection("users").document(passengerId).get().await()
                    if (!userSnapshot.exists()) {
                        return@forEach
                    }

                    val user = userSnapshot.toObject(UserModel::class.java)
                    if (user == null) {
                        Log.w(
                            "fetchRides",
                            "Failed to parse user from document: $userSnapshot.data"
                        )
                        return@forEach
                    }

                    val ride = RideRequest(trip, user, documentId)
                    rides.add(ride)
                } catch (e: Exception) {
                    Log.e("fetchRides", "Error fetching ride data for document ID: $documentId", e)
                }
            }

            return@withContext rides
        }


    fun cancelRideAndResetRequests(
        postDocumentId: String,
        requestedDocumentIds: List<String>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (postDocumentId.isEmpty()) {
            onFailure(IllegalArgumentException("Invalid postDocumentId"))
            return
        }

        val db = FirebaseFirestore.getInstance()

        db.collection("immediatePostedTrips").document(postDocumentId)
            .delete()
            .addOnSuccessListener {
                deletRidePost(
                    requestedDocumentIds,
                    onUpdateSuccess = {
                        onSuccess()
                    },
                    onUpdateFailure = { e ->
                        onFailure(e)
                    }
                )
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }


    fun deletRidePost(
        requestedDocumentIds: List<String>,
        onUpdateSuccess: () -> Unit,
        onUpdateFailure: (Exception) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()

        if (requestedDocumentIds.isEmpty()) {
            onUpdateSuccess()
            return
        }

        var updateCount = 0
        val totalUpdates = requestedDocumentIds.size
        requestedDocumentIds.forEach { documentId ->
            db.collection("immediateRequestedTrips").document(documentId)
                .update(
                    mapOf(
                        "driverId" to "",
                        "requestStatus" to "pending"
                    )
                ).addOnSuccessListener {
                    updateCount++
                    if (updateCount == totalUpdates) {
                        onUpdateSuccess()
                    }
                }.addOnFailureListener { e ->
                    onUpdateFailure(e)
                }
        }
    }



    fun rejectRideRequest(
        documentId: String,
        onUpdateSuccess: () -> Unit,
        onUpdateFailure: (Exception) -> Unit
    ) {
        if (documentId.isEmpty()) {
            onUpdateFailure(IllegalArgumentException("Invalid document ID"))
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("immediateRequestedTrips").document(documentId)
            .update(
                "driverId", "",
                "requestStatus", "pending"
            )
            .addOnSuccessListener {
                onUpdateSuccess()
            }
            .addOnFailureListener { e ->
                onUpdateFailure(e)
            }
    }


    fun acceptRideRequest(
        documentId: String,

    ) {

        val db = FirebaseFirestore.getInstance()
        db.collection("immediateRequestedTrips").document(documentId)
            .update(

                "requestStatus", "accepted"
            )

    }


    suspend fun decrementAvailableSeats(tripId: String, collectionName: String = "immediatePostedTrips"): Int {
        val db = FirebaseFirestore.getInstance()
        val tripRef = db.collection(collectionName).document(tripId)

        return try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(tripRef)
                val currentSeats = snapshot.getLong("availableSeats") ?: throw IllegalStateException("Seats count missing")
                val newSeats = (currentSeats - 1).coerceAtLeast(0)

                transaction.update(tripRef, "availableSeats", newSeats)
                newSeats.toInt()
            }.await()
        } catch (e: Exception) {
            throw e
        }
    }



    suspend fun updateAcceptedRideRequestsStatus(acceptedRideRequestIds: List<String>, status : String) = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()

        acceptedRideRequestIds.forEach { requestId ->
            val requestRef = db.collection("immediateRequestedTrips").document(requestId)
            batch.update(requestRef, "tripStatus", status)
        }

        batch.commit().await()
    }

    suspend fun addToRidesCollection(tripId: String, rideData: Map<String, Any?>) = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val ridesCollectionRef = db.collection("rides")

        ridesCollectionRef.document(tripId)
            .set(rideData).await()
    }

    suspend fun deletePostedTrip(postedTripDocumentId: String) = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        db.collection("immediatePostedTrips").document(postedTripDocumentId)
            .delete().await()
    }

    fun getDriverId() : String? {

        val driverId = FirebaseAuth.getInstance().currentUser?.uid
        return driverId;

    }

    fun getImmediateRequestedRideReference(rideId: String): DocumentReference {
        return db.collection("immediateRequestedTrips").document(rideId)
    }

    fun getRidesReference(): CollectionReference {
        return db.collection("rides")
    }

    fun deleteChatFromFirestore(chatId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("Chats").document(chatId)
            .collection("messages")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    db.collection("Chats").document(chatId)
                        .collection("messages")
                        .document(document.id)
                        .delete()
                        .addOnSuccessListener {
                            Log.d("SubcollectionDeletion", "Successfully deleted subcollection document with ID: ${document.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("SubcollectionDeletion", "Error deleting subcollection document with ID: ${document.id}", e)
                        }
                }
                db.collection("Chats").document(chatId)
                    .delete()
                    .addOnSuccessListener {
                        Log.d("ChatDeletion", "Successfully deleted chat with ID: $chatId")
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatDeletion", "Error deleting chat with ID: $chatId", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("SubcollectionRetrieval", "Error retrieving subcollection documents for chat with ID: $chatId", e)
            }
    }

}


