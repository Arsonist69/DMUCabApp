package com.example.mydmucabapp_driver.controller

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.helpers.Validator
import com.example.mydmucabapp_driver.model.DataClass.UserModel
import com.example.mydmucabapp_driver.model.DataHandling.UserRepository
import com.google.firebase.auth.FirebaseAuth

class RegistrationActivity : AppCompatActivity() {

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

            if (!validateForm(
                    email,
                    name,
                    phoneNo,
                    password,
                    confPassword
                )
            ) return@setOnClickListener



            val userRepository = UserRepository()
            val driver = UserModel(userId = "", name = name, email = email, phone = phoneNo, passengerAccountIsActive = false)
            registerDriver(driver, password, userRepository)

        }
    }


    private fun validateForm(
        email: String,
        name: String,
        phoneNo: String,
        password: String,
        confPassword: String
    ): Boolean {
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


    private fun registerDriver(
        driver: UserModel,
        password: String,
        userRepository: UserRepository
    ) {
        progressBarLogin.visibility = View.VISIBLE
        registerButton.isEnabled = false
        userRepository.checkIfEmailExists(driver.email) { exists, userId ->
            if (exists && userId != null) { // Ensure userId is not null
                userRepository.getDriverRoleStatusByEmail(driver.email) { driverStatus ->
                    if (driverStatus == false) {
                        runOnUiThread {
                            AlertDialog.Builder(this@RegistrationActivity)
                                .setTitle("Registration Notice")
                                .setMessage("You are already registered as a passenger. To continue driving with us, please login and upload more details.")
                                .setPositiveButton("Login") { dialog, which ->
                                    // Here, instead of just navigating to LoginActivity,
                                    // activate the driver role for the user.
                                    userRepository.activateDriverRole(userId) { isSuccess ->
                                        if (isSuccess) {
                                            // Role updated successfully, now navigate to the LoginActivity.
                                            val intent = Intent(this@RegistrationActivity, LoginActivity::class.java)
                                            startActivity(intent)
                                            finish()
                                        } else {
                                            // Handle failure to update role, maybe show a Toast or log this issue.
                                            Toast.makeText(this@RegistrationActivity, "Failed to update role. Please try again.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                        progressBarLogin.visibility = View.GONE
                        registerButton.isEnabled = true
                    } else if (driverStatus == true) {
                        // User is already a driver, remind them to log in.
                        showAlreadyRegisteredAsDriver()
                    } else {
                        // Handle other cases, such as error occurred.
                        showErrorOccurred()
                    }
                }
            } else if (!exists) {
                // Proceed with a new driver registration if the email does not exist in the database.
                handleNewDriverRegistration(driver, password, userRepository)
            }
        }
    }

    private fun showAlreadyRegisteredAsDriver() {
        runOnUiThread {
            Toast.makeText(
                this@RegistrationActivity,
                "Email already exists.",
                Toast.LENGTH_SHORT
            ).show()

        }
        progressBarLogin.visibility = View.GONE
        registerButton.isEnabled = true
    }

    private fun showErrorOccurred() {
        runOnUiThread {
            Toast.makeText(
                this@RegistrationActivity,
                "An error occurred, please try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
        progressBarLogin.visibility = View.GONE
        registerButton.isEnabled = true
    }

    private fun handleNewDriverRegistration(driver: UserModel, password: String, userRepository: UserRepository) {
        userRepository.registerDriver(driver, password) { success, emailSent ->
            runOnUiThread {
                if (success && emailSent) {
                    val updatedDriver = driver.copy(userId = FirebaseAuth.getInstance().currentUser?.uid ?: "")
                    val intent = Intent(this@RegistrationActivity, EmailVerification::class.java).apply {
                        putExtra("driver", updatedDriver)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@RegistrationActivity, "Registration failed.", Toast.LENGTH_SHORT).show()
                }
                progressBarLogin.visibility = View.GONE
                registerButton.isEnabled = true
            }
        }
    }




}
