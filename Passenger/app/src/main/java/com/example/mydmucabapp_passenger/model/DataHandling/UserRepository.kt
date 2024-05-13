package com.example.mydmucabapp_passenger.model.DataHandling

import android.net.Uri
import android.util.Log
import com.example.mydmucabapp_passenger.model.DataClass.RideRequest
import com.example.mydmucabapp_passenger.model.DataClass.Trips
import com.example.mydmucabapp_passenger.model.DataClass.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class UserRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage = Firebase.storage


    fun registerPassenger(passenger: UserModel, password: String, onComplete: (Boolean, Boolean) -> Unit) {
        auth.createUserWithEmailAndPassword(passenger.email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.sendEmailVerification()?.addOnCompleteListener { emailTask ->
                    if (emailTask.isSuccessful) {
                        onComplete(true, true) // Registration succeeded and verification email sent
                    } else {
                        onComplete(true, false) // Registration succeeded but verification email not sent
                    }
                }
            } else {
                onComplete(false, false)
            }
        }
    }

    fun checkEmailVerificationAndSaveData(passenger: UserModel, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        user?.reload()?.addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful && user.isEmailVerified) {

                val userInfo = UserModel(
                    userId = user.uid,
                    name = passenger.name,
                    email = passenger.email,
                    phone = passenger.phone,
                    passengerAccountIsActive = passenger.passengerAccountIsActive
                )
                db.collection("users").document(user.uid).set(userInfo)
                    .addOnSuccessListener {
                        onComplete(true)
                    }
                    .addOnFailureListener {
                        onComplete(false)
                    }
            } else {
                onComplete(false)
            }
        }
    }

    fun checkIfEmailExists(email: String, callback: (Boolean) -> Unit) {
        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    callback(false)
                } else {
                    callback(true)
                }
            }
            .addOnFailureListener { exception ->
                Log.d("Firestore", "Error checking for email existence: ", exception)
                callback(false)
            }
    }

    interface UserNameCallback {
        fun onUserNameReceived(userName: String?)
    }

    fun getUserName(uId: String, callback: UserNameCallback) {
        val docRef = db.collection("users").document(uId)
        docRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val document = task.result
                if (document != null && document.exists()) {
                    val userName = document.getString("name")
                    callback.onUserNameReceived(userName)
                } else {
                    callback.onUserNameReceived(null)
                }
            } else {
                callback.onUserNameReceived(null)
            }
        }
    }

    interface getEmailCallback {
        fun onEmailReceived(Email: String?)
    }

    fun getEmail(uId: String, callback: getEmailCallback) {
        val docRef = db.collection("users").document(uId)
        docRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val document = task.result
                if (document != null && document.exists()) {
                    val userName = document.getString("email")
                    callback.onEmailReceived(userName)
                } else {
                    callback.onEmailReceived(null)
                }
            } else {
                callback.onEmailReceived(null)
            }
        }
    }

    fun uploadProfileImage(imageUri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")
        val imageRef = storage.reference.child("profile_images/$uid.jpg")

        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    onSuccess(imageUrl)
                }.addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun saveProfileImageUrl(imageUrl: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")

        db.collection("users").document(uid)
            .update("profileImageUrl", imageUrl)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun fetchProfileImageUrl(uId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("users").document(uId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val imageUrl = documentSnapshot.getString("profileImageUrl")
                    onSuccess(imageUrl)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }


    suspend fun fetchDriverIds(collectionIds: ArrayList<String>): List<String> {
        val db = FirebaseFirestore.getInstance()
        return withContext(Dispatchers.IO) {
            val deferredResults = collectionIds.map { collectionId ->
                async {
                    val documentSnapshot = try {
                        db.collection("immediatePostedTrips").document(collectionId).get().await()
                    } catch (e: Exception) {
                        null
                    }
                    documentSnapshot?.getString("driverId")
                }
            }
            deferredResults.awaitAll().filterNotNull()
        }
    }

     fun fetchDriverDetails(driverId: String, completion: (Map<String, Any?>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .document(driverId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    completion(document.data ?: emptyMap())
                } else {
                    completion(emptyMap())
                }
            }
            .addOnFailureListener { exception ->
                Log.e("DriverDetailsDialogFragment", "Error fetching driver details", exception)
            }
    }

    suspend fun fetchRides(documentIds: List<String>, collectionName: String = "immediatePostedTrips"): List<RideRequest> = withContext(Dispatchers.IO) {
        val rides = mutableListOf<RideRequest>()
        val db = FirebaseFirestore.getInstance()

        documentIds.forEach { documentId ->
            try {
                val tripSnapshot = db.collection(collectionName).document(documentId).get().await()

                if (!tripSnapshot.exists()) {
                    Log.w("fetchRides", "No trip found with document ID: $documentId")
                    return@forEach
                }

                val trip = tripSnapshot.toObject(Trips::class.java)
                if (trip == null) {
                    Log.w("fetchRides", "Failed to parse trip from document: $tripSnapshot.data")
                    return@forEach
                }

                val driverId = tripSnapshot.getString("driverId")
                if (driverId == null) {
                    Log.w("fetchRides", "No driverId found in trip document: $documentId")
                    return@forEach
                }

                val userSnapshot = db.collection("users").document(driverId).get().await()
                if (!userSnapshot.exists()) {
                    Log.w("fetchRides", "No user found with driver ID: $driverId")
                    return@forEach
                }

                val user = userSnapshot.toObject(UserModel::class.java)
                if (user == null) {
                    Log.w("fetchRides", "Failed to parse user from document: $userSnapshot.data")
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



    suspend fun fetchDriverDetailsSuspend(driverId: String): Map<String, Any?> =
        suspendCoroutine { continuation ->
            fetchDriverDetails(driverId) { data ->
                continuation.resume(data)
            }
        }

    fun getPassengerId() : String? {

        val Id = FirebaseAuth.getInstance().currentUser?.uid
        return Id;

    }

}




