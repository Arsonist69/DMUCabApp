package com.example.mydmucabapp_driver.controller

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.model.DataHandling.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class UploadDocumentActivity : AppCompatActivity() {

    private lateinit var etLicenceCheck: EditText
    private lateinit var etVehicleRegisteration: EditText
    private lateinit var buttonUploadInsurance: Button
    private lateinit var buttonUploadID: Button
    private lateinit var buttonSubmitCheckCode : Button
    private lateinit var buttonSubmitVRN : Button
    private lateinit var tvVerification : TextView
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val PICK_IMAGE_ID_REQUEST = 2
    private val PICK_IMAGE_INSURANCE_REQUEST = 3
    private val PERMISSIONS_REQUEST = 100
    private lateinit var vehicleDetailsLayout: LinearLayout
    private lateinit var tvVehicleDetails: TextView
    private lateinit var etVehicleModel: EditText
    private lateinit var etVehicleColor: EditText

    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload_document)

        etLicenceCheck = findViewById(R.id.editTextLicenseCheck)
        etVehicleRegisteration = findViewById(R.id.editTextVehicleRegistration)
        buttonUploadInsurance = findViewById(R.id.btnUploadInsurance)
        buttonUploadID = findViewById(R.id.btnUploadId)
        buttonSubmitCheckCode = findViewById(R.id.btnSubmitLicenseCode)
        buttonSubmitVRN = findViewById(R.id.btnSubmitVRN)
        tvVerification = findViewById(R.id.tvVerification)
        vehicleDetailsLayout = findViewById(R.id.vehicleDetailsLayout);
        tvVehicleDetails = findViewById(R.id.tvVehicleDetails);
        vehicleDetailsLayout = findViewById(R.id.vehicleDetailsLayout)
        etVehicleModel = findViewById(R.id.editTextVehicleModel)
        etVehicleColor = findViewById(R.id.editTextColor)

        disableInsuranceButton()
        disableIdButton()
        disableVRNButton()
        disableLicenseSubmitButton()


        changeTextView();


        setupListeners()

    }

    private fun setupListeners() {
        buttonSubmitCheckCode.setOnClickListener {
            val licenseCheckCode = etLicenceCheck.text.toString()
            updateUserField("driverLicenseCheckCode", licenseCheckCode)
        }

        buttonSubmitVRN.setOnClickListener {
            val vehicleRegistration = etVehicleRegisteration.text.toString().trim()
            val vehicleModel = etVehicleModel.text.toString().trim()
            val vehicleColor = etVehicleColor.text.toString().trim()

            if (vehicleRegistration.isNotEmpty() && vehicleModel.isNotEmpty() && vehicleColor.isNotEmpty()) {
                val updates = mapOf(
                    "vehicleRegistration" to vehicleRegistration,
                    "vehicleModel" to vehicleModel,
                    "vehicleColor" to vehicleColor
                )
                updateUserFields(updates)
            } else {
                Toast.makeText(this, "All fields must be filled out.", Toast.LENGTH_SHORT).show()
            }
        }

        buttonUploadID.setOnClickListener {
            if(checkAndRequestPermissions()){
            openImageChooser(PICK_IMAGE_ID_REQUEST)}
        }

        buttonUploadInsurance.setOnClickListener {
            if(checkAndRequestPermissions()){
            openImageChooser(PICK_IMAGE_INSURANCE_REQUEST)}
        }

        tvVehicleDetails.setOnClickListener {
            toggleVisibility()
        }
    }
    private fun toggleVisibility() {
        if (vehicleDetailsLayout.visibility == View.GONE) {
            vehicleDetailsLayout.visibility = View.VISIBLE
        } else {
            vehicleDetailsLayout.visibility = View.GONE
        }
    }

    private fun updateUserField(field: String, value: String) {
        if (value.isNotEmpty()) {
            val updateMap = mapOf(field to value as Any)

            userRepository.updateUserDocument(updateMap) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Please enter a valid $field.", Toast.LENGTH_SHORT).show()
        }
    }



    private fun updateUserFields(updates: Map<String, Any>) {
        if (updates.values.none { (it as? String)?.isEmpty() == true }) {
            userRepository.updateUserDocument(updates) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Please fill all the fields correctly.", Toast.LENGTH_SHORT).show()
        }
    }





    private fun checkAndRequestPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        val listPermissionsNeeded = ArrayList<String>()

        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            if (imagePermission != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            val readStoragePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (readStoragePermission != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), PERMISSIONS_REQUEST)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST -> {
                val grantResultsArray = grantResults.map { it }.toTypedArray()

                val allPermissionsGranted = grantResultsArray.all { it == PackageManager.PERMISSION_GRANTED }
                if (grantResults.isNotEmpty() && allPermissionsGranted) {
                    Toast.makeText(this, "All required permissions granted. You can now proceed.", Toast.LENGTH_SHORT).show()
                } else {

                    val deniedPermissions = permissions.zip(grantResultsArray)
                        .filter { (_, result) -> result != PackageManager.PERMISSION_GRANTED }
                        .map { (permission, _) -> permission }

                    Toast.makeText(this, "Permission denied for: ${deniedPermissions.joinToString(", ")}. Please grant required permissions.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openImageChooser(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            data.data?.let { uri ->
                when (requestCode) {
                    PICK_IMAGE_ID_REQUEST -> {
                        uploadDocument(uri, "idDocument")
                    }
                    PICK_IMAGE_INSURANCE_REQUEST -> {
                        uploadDocument(uri, "insuranceCertificateImageUrl")
                    }
                }
            }
        }
    }
    private fun uploadDocument(uri: Uri, field: String) {
        userRepository.uploadDocuments(uri,
            onSuccess = { imageUrl ->
                userRepository.saveDocumentsUrl(imageUrl, field,
                    onSuccess = {
                        Toast.makeText(this, "$field updated successfully.", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(this, "Failed to save $field URL: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                )
            },
            onFailure = { e ->
                Toast.makeText(this, "Failed to upload document: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }


    private fun disableInsuranceButton() {
        userRepository.checkinsuranceCertificateImageUrl(object : UserRepository.CheckResultCallback {
            override fun onCheckComplete(exists: Boolean) {
                runOnUiThread {
                    if (exists) {
                        buttonUploadInsurance.isEnabled = false
                        buttonUploadInsurance.alpha = 0.5f
                    } else {
                        buttonUploadInsurance.isEnabled = true
                        buttonUploadInsurance.alpha = 1.0f
                    }
                }
                changeTextView();

            }

        })
    }

    private fun disableIdButton() {
        userRepository.checkIdDocument(object : UserRepository.CheckResultCallback {
            override fun onCheckComplete(exists: Boolean) {
                runOnUiThread {
                    if (exists) {
                        buttonUploadID.isEnabled = false
                        buttonUploadID.alpha = 0.5f
                    } else {
                        buttonUploadID.isEnabled = true
                        buttonUploadID.alpha = 1.0f
                    }
                    changeTextView();

                }
            }
        })
    }

    private fun disableLicenseSubmitButton() {
        userRepository.checkDriverLicenseCheckCode(object : UserRepository.CheckResultCallback {
            override fun onCheckComplete(exists: Boolean) {
                runOnUiThread {
                    if (exists) {
                        buttonSubmitCheckCode.isEnabled = false
                        buttonSubmitCheckCode.alpha = 0.5f
                    } else {
                        buttonSubmitCheckCode.isEnabled = true
                        buttonSubmitCheckCode.alpha = 1.0f
                    }
                    changeTextView();

                }
            }
        })
    }

    private fun disableVRNButton() {
        userRepository.checkVehicleRegisteration(object : UserRepository.CheckResultCallback {
            override fun onCheckComplete(exists: Boolean) {
                runOnUiThread {
                    if (exists) {
                        buttonSubmitVRN.isEnabled = false
                        buttonSubmitVRN.alpha = 0.5f
                    } else {
                        buttonSubmitVRN.isEnabled = true
                        buttonSubmitVRN.alpha = 1.0f
                    }
                    changeTextView();

                }
            }
        })
    }

    private fun changeTextView() {
        val allDisabled = !buttonUploadInsurance.isEnabled && !buttonUploadID.isEnabled &&
                !buttonSubmitCheckCode.isEnabled && !buttonSubmitVRN.isEnabled

        tvVerification.text = if (allDisabled) {
            "Your documents are being processed"
        } else {
            "Please Upload the required documents to complete your driver registration"
        }
    }


}