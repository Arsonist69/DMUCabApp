package com.example.mydmucabapp_passenger.controller

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.mydmucabapp_passenger.R
import com.example.mydmucabapp_passenger.helpers.Validator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var buttonRegister: Button
    private lateinit var buttonLogin: Button
    private lateinit var txtEmail: EditText
    private lateinit var txtPassword: EditText
    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth
    private lateinit var progressBarLogin: ProgressBar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initializeViews()
        initializeFirebaseAuth()
        setupToolbar()
        setupListeners()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        buttonRegister = findViewById(R.id.btnRegister)
        buttonLogin = findViewById(R.id.btnLogin)
        txtEmail = findViewById(R.id.email_log)
        txtPassword = findViewById(R.id.password_log)
        progressBarLogin = findViewById(R.id.progressBarLogin)

    }

    private fun initializeFirebaseAuth() {
        auth = FirebaseAuth.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupListeners() {
        buttonRegister.setOnClickListener {
            navigateToRegistration()
        }

        buttonLogin.setOnClickListener {
            loginUser()
        }
    }

    private fun navigateToRegistration() {
        startActivity(Intent(this, RegisterationActivity::class.java))
    }

    private fun loginUser() {
        val email = txtEmail.text.toString().trim()
        val password = txtPassword.text.toString().trim()

        if (!validateInputs(email, password)) return

        progressBarLogin.visibility = View.VISIBLE
        buttonLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
            progressBarLogin.visibility = View.GONE
            buttonLogin.isEnabled = true

            if (task.isSuccessful) {
                FirebaseMessaging.getInstance().subscribeToTopic(FirebaseAuth.getInstance().currentUser!!.uid)
                Toast.makeText(baseContext, "Login successful.", Toast.LENGTH_SHORT).show()
                navigateToNextActivity()
            } else {
                handleLoginFailure(task.exception)
            }
        }
    }


    private fun validateInputs(email: String, password: String): Boolean {
        if (!Validator.isValidEmail(email)) {
            txtEmail.error = "Invalid Email Address (e.g., name@my365.dmu.ac.uk)"
            return false
        }
        if (password.isEmpty()) {
            txtPassword.error = "Password is required."
            return false
        }
        return true
    }

    private fun handleLoginFailure(exception: Exception?) {
        when (exception) {
            is FirebaseAuthInvalidCredentialsException -> Toast.makeText(baseContext, "Invalid credentials.", Toast.LENGTH_SHORT).show()
            is FirebaseAuthInvalidUserException -> Toast.makeText(baseContext, "User does not exist.", Toast.LENGTH_SHORT).show()
            else -> Toast.makeText(baseContext, "Login failed: ${exception?.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToNextActivity() {
        val intent = Intent(this,  DashBoardActivity::class.java )
        startActivity(intent)
        finish()
    }
}
