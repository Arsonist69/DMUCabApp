package com.example.mydmucabapp_passenger.controller

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.example.mydmucabapp_passenger.R
import com.example.mydmucabapp_passenger.helpers.Validator
import com.example.mydmucabapp_passenger.model.DataClass.UserModel
import com.example.mydmucabapp_passenger.model.DataHandling.UserRepository
import com.google.firebase.auth.FirebaseAuth

class RegisterationActivity : AppCompatActivity() {

    private lateinit var txtEmail: EditText
    private lateinit var txtName: EditText
    private lateinit var txtPhoneNo: EditText
    private lateinit var txtPassword: EditText
    private lateinit var txtConfPass: EditText
    private lateinit var registerButton: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var toolbar: Toolbar
    private lateinit var progressBarLogin: ProgressBar


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registeration)

        initializeViews()
        setupToolbar()
        setupRegisterButton()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        txtEmail = findViewById(R.id.edit_txt_email_reg)
        txtName = findViewById(R.id.edit_txt_name)
        txtPhoneNo = findViewById(R.id.edit_txt_phoneno)
        txtPassword = findViewById(R.id.edit_txt_pass_reg)
        txtConfPass = findViewById(R.id.edit_txt_conf_pass_reg)
        registerButton = findViewById(R.id.btnRegister)
        auth = FirebaseAuth.getInstance()
        progressBarLogin = findViewById(R.id.progressBarLogin)

    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRegisterButton() {
        registerButton.setOnClickListener {



            val email = txtEmail.text.toString()
            val name = txtName.text.toString()
            val phoneNo = txtPhoneNo.text.toString()
            val password = txtPassword.text.toString()
            val confPassword = txtConfPass.text.toString()

            if (!validateForm(email, name, phoneNo, password, confPassword)) return@setOnClickListener

            progressBarLogin.visibility = View.VISIBLE
            registerButton.isEnabled = false

            val userRepository = UserRepository()
            val passenger = UserModel(userId = "", name = name, email = email, phone = phoneNo, passengerAccountIsActive = true)
            registerPassenger(passenger, password, userRepository)
            progressBarLogin.visibility = View.GONE
            registerButton.isEnabled = true
        }
    }















    private fun validateForm(email: String, name: String, phoneNo: String, password: String, confPassword: String): Boolean {
        if (!Validator.isValidEmail(email)) {
            txtEmail.error = "Invalid Email Address (e.g., name@my365.dmu.ac.uk)"
            return false
        }

        if (!Validator.isValidFullName(name)) {
            txtName.error = "Invalid Full Name (up to 15 characters)"
            return false
        }

        if (!Validator.isValidPhoneNumber(phoneNo)) {
            txtPhoneNo.error = "Invalid Phone Number (e.g., +441234567891 or 07123456789)"
            return false
        }

        if (!Validator.isStrongPassword(password)) {
            txtPassword.error = "Invalid Password (at least 7 characters with letters and numbers)"
            return false
        }

        if (password != confPassword) {
            txtConfPass.error = "Passwords do not match"
            return false
        }

        return true
    }


    private fun registerPassenger(passenger: UserModel, password: String, userRepository: UserRepository) {

        userRepository.checkIfEmailExists(passenger.email) { emailExists ->
            if (emailExists) {
                Toast.makeText(this, "Email already exists.", Toast.LENGTH_SHORT).show()
            } else {
                userRepository.registerPassenger(passenger, password) { success, emailSent ->
                    if (success && emailSent) {
                        val userId = auth.currentUser?.uid ?: ""
                        val updatedPassenger = passenger.copy(userId = userId)
                        val intent = Intent(this, EmailVerification::class.java).apply {
                            putExtra("passenger", updatedPassenger)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
