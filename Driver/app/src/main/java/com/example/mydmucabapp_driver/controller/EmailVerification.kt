package com.example.mydmucabapp_driver.controller

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.model.DataClass.UserModel
import com.example.mydmucabapp_driver.model.DataHandling.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmailVerification : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_verification)


        // Initialize the Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Enable the back button in the Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true);
        supportActionBar?.setDisplayShowHomeEnabled(true);

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }


        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        pollForEmailVerification()

    }

    private fun pollForEmailVerification() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                auth.currentUser?.reload()?.addOnCompleteListener { task ->
                    if (task.isSuccessful && auth.currentUser?.isEmailVerified == true) {
                        storeUserDetailsInFirestore()
                    } else {
                        handler.postDelayed(this, 5000) // Check every 5 seconds
                    }
                }
            }
        }
        handler.post(runnable)
    }

    private fun storeUserDetailsInFirestore() {
        val driver = intent.getSerializableExtra("driver") as? UserModel

        driver?.let {
            db.collection("users").document(it.userId).set(it)
                .addOnSuccessListener {
                    Toast.makeText(this, "User details saved successfully", Toast.LENGTH_SHORT)
                        .show()

                    val userRepository = UserRepository()

                    auth.currentUser?.uid?.let { userId ->
                        userRepository.checkDriverAccountIsActive(userId) { isActive ->
                            val intent = if (isActive == true) {
                                // User is an active driver, navigate to DashboardActivity
                                Intent(this, DashBoardActivity::class.java)
                            } else {
                                // User is not an active driver or the status couldn't be determined, navigate to DocumentsCheckActivity
                                Intent(this, UploadDocumentActivity::class.java)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Failed to save user details: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } ?: run {
            Toast.makeText(this, "Error retrieving user details.", Toast.LENGTH_SHORT).show()
        }
    }
}