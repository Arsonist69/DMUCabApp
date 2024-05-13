package com.example.mydmucabapp_passenger.controller

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.mydmucabapp_passenger.R
import com.example.mydmucabapp_passenger.model.DataClass.UserModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class EmailVerification : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_verification)



        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
                        handler.postDelayed(this, 5000)
                    }
                }
            }
        }
        handler.post(runnable)
    }

    private fun storeUserDetailsInFirestore() {

        val passenger = intent.getSerializableExtra("passenger") as? UserModel

        passenger?.let {
            db.collection("users").document(it.userId).set(it)
                .addOnSuccessListener {v->
                    Toast.makeText(this, "User details saved successfully", Toast.LENGTH_SHORT).show()
                    val loginIntent = Intent(this, LoginActivity::class.java)
                    startActivity(loginIntent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save user details: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "Error retrieving user details.", Toast.LENGTH_SHORT).show()
        }
    }

}