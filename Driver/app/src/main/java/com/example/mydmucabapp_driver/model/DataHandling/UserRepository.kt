package com.example.mydmucabapp_driver.model.DataHandling

import android.net.Uri
import android.util.Log
import com.example.mydmucabapp_driver.model.DataClass.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class UserRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val storage = Firebase.storage


    fun registerDriver(driver: UserModel, password: String, onComplete: (Boolean, Boolean) -> Unit) {
        auth.createUserWithEmailAndPassword(driver.email, password).addOnCompleteListener { task ->
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
                onComplete(false, false) // Registration failed
            }
        }
    }

    fun checkEmailVerificationAndSaveData(driver: UserModel, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        user?.reload()?.addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful && user.isEmailVerified) {

                val userInfo = UserModel(
                    userId = user.uid,
                    name = driver.name,
                    email = driver.email,
                    phone = driver.phone,
                    passengerAccountIsActive = false
                )
                // Save the user info in Firestore
                db.collection("users").document(user.uid).set(userInfo)
                    .addOnSuccessListener {
                        onComplete(true) // Data saved successfully
                    }
                    .addOnFailureListener {
                        onComplete(false) // Failed to save data
                    }
            } else {
                onComplete(false) // Email not verified
            }
        }
    }

    fun checkIfEmailExists(email: String, callback: (exists: Boolean, userId: String?) -> Unit) {
        db.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    callback(false, null) // Email does not exist
                } else {
                    // Assuming the email field is unique, get the first document
                    val userId = documents.documents[0].id
                    callback(true, userId) // Email exists, return the user ID
                }
            }
            .addOnFailureListener { exception ->
                Log.d("Firestore", "Error checking for email existence: ", exception)
                callback(false, null) // Error occurred, handle as if the email does not exist
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

    fun getDriverRoleStatusByEmail(email: String, onComplete: (Boolean?) -> Unit) {
        // First, find the user ID by email
        checkIfEmailExists(email) { exists, userId ->
            if (exists && userId != null) {
                // Now that we have the user ID, fetch the role status
                val docRef = db.collection("users").document(userId)
                docRef.get().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val document = task.result
                        val roles = document?.data?.get("roles") as? Map<String, Boolean>
                        val driverStatus = roles?.get("driver")
                        onComplete(driverStatus) // Could be true, false, or null
                    } else {
                        onComplete(null) // Task failed, or document does not exist
                    }
                }
            } else {
                onComplete(null) // Email does not exist or error occurred
            }
        }
    }


    fun activateDriverRole(userId: String, onComplete: (Boolean) -> Unit) {
        val userRolesUpdate = mapOf("roles.driver" to true)

        db.collection("users").document(userId)
            .update(userRolesUpdate)
            .addOnSuccessListener {
                Log.d("UserRepository", "Driver role activated successfully for user ID: $userId")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("UserRepository", "Error updating driver role for user ID: $userId", e)
                onComplete(false)
            }
    }

    fun checkDriverAccountIsActive(userId: String, onComplete: (Boolean?) -> Unit) {
        val docRef = db.collection("users").document(userId)
        docRef.get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val document = task.result
                if (document != null && document.exists()) {
                    // Retrieve the driverAccountIsActive field
                    val isActive = document.getBoolean("driverAccountIsActive")
                    onComplete(isActive) // isActive could be true, false, or null if not set
                } else {
                    onComplete(null) // Document does not exist
                }
            } else {
                onComplete(null) // Task failed to execute properly
            }
        }
    }

    fun updateUserDocument(updates: Map<String, Any>, onComplete: (Boolean, String) -> Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener {
                    onComplete(true, "Update successful.")
                }
                .addOnFailureListener { e ->
                    onComplete(false, "Failed to update: ${e.message}")
                }
        } else {
            onComplete(false, "Error: User not recognized.")
        }
    }









    fun uploadDocuments(imageUri: Uri, onSuccess: (String) -> Unit, onFailure: (Exception) -> Unit) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")
        val imageRef = storage.reference.child("driver_documents/$uid.jpg")

        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    onSuccess(imageUrl)
                }.addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun saveDocumentsUrl(imageUrl: String, field: String , onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in")

        db.collection("users").document(uid)
            .update("$field", imageUrl)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    /**fun fetchProfileImageUrl(uId: String, onSuccess: (String?) -> Unit, onFailure: (Exception) -> Unit) {
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
    }**/




    interface CheckResultCallback {
        fun onCheckComplete(exists: Boolean)
    }

    fun checkDriverLicenseCheckCode(callback: CheckResultCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onCheckComplete(false)
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.contains("driverLicenseCheckCode") && document.getString("driverLicenseCheckCode") != null) {
                    callback.onCheckComplete(true) // License check code exists
                } else {
                    callback.onCheckComplete(false) // License check code does not exist
                }
            }
            .addOnFailureListener {
                callback.onCheckComplete(false) // Handle failure as "not exists" or differentiate based on your needs
            }
    }

    fun checkIdDocument(callback: CheckResultCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onCheckComplete(false)
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.contains("idDocument") && document.getString("idDocument") != null) {
                    callback.onCheckComplete(true)
                } else {
                    callback.onCheckComplete(false)
                }
            }
            .addOnFailureListener {
                callback.onCheckComplete(false) // Handle failure as "not exists" or differentiate based on your needs
            }
    }

    fun checkinsuranceCertificateImageUrl
                (callback: CheckResultCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onCheckComplete(false)
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.contains("insuranceCertificateImageUrl") && document.getString("insuranceCertificateImageUrl") != null) {
                    callback.onCheckComplete(true)
                } else {
                    callback.onCheckComplete(false)
                }
            }
            .addOnFailureListener {
                callback.onCheckComplete(false)
            }
    }
    fun checkVehicleRegisteration
                (callback: CheckResultCallback) {
        val uid = auth.currentUser?.uid ?: return callback.onCheckComplete(false)
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.contains("vehicleRegistration") && document.getString("vehicleRegistration") != null) {
                    callback.onCheckComplete(true)
                } else {
                    callback.onCheckComplete(false)
                }
            }
            .addOnFailureListener {
                callback.onCheckComplete(false)
            }
    }








}



