package com.example.mydmucabapp_driver.controller

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.databinding.ActivityDashBoardBinding
import com.example.mydmucabapp_driver.model.DataHandling.UserRepository
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth

class DashBoardActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityDashBoardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var profileImageView: ShapeableImageView
    private lateinit var txtName: TextView
    private lateinit var txtEmail: TextView
    private val PICK_IMAGE_REQUEST = 1
    private val PERMISSIONS_REQUEST = 100
    private lateinit var userRepository: UserRepository

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeActionBar()
        initializeNavigationDrawer()
        initializeUserProfile()

        profileImageView.setOnClickListener {
            if (checkAndRequestPermissions()) {
                openImageChooser()
            }
        }
    }

    private fun initializeActionBar() {
        setSupportActionBar(binding.appBarDashBoardActivty.toolbar)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.dash_board, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_logout -> {
                FirebaseAuth.getInstance().signOut()
                Toast.makeText(this, "Logout Successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this,LoginActivity::class.java))
                finishAffinity()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_dash_board_activty)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun initializeNavigationDrawer() {
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_dash_board_activty)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_main_dashboard, R.id.nav_user_details, R.id.nav_scheduled_trips, R.id.nav_rides_history, R.id.nav_faqs), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    private fun initializeUserProfile() {
        auth = FirebaseAuth.getInstance()
        userRepository = UserRepository()

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        val headerView = navigationView.getHeaderView(0)
        profileImageView = headerView.findViewById(R.id.imgProfilePicture)
        txtName = headerView.findViewById(R.id.txtName)
        txtEmail = headerView.findViewById(R.id.txtEmail)

        auth.currentUser?.uid?.let { userId ->
            updateUserNameAndEmail(userId)
            fetchAndDisplayProfileImage(userId)
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
                // Convert grantResults to Array<Int> for zip function
                val grantResultsArray = grantResults.map { it }.toTypedArray()

                // Check if all requested permissions have been granted
                val allPermissionsGranted = grantResultsArray.all { it == PackageManager.PERMISSION_GRANTED }
                if (grantResults.isNotEmpty() && allPermissionsGranted) {
                    openImageChooser()
                } else {

                    val deniedPermissions = permissions.zip(grantResultsArray)
                        .filter { (_, result) -> result != PackageManager.PERMISSION_GRANTED }
                        .map { (permission, _) -> permission }

                    Toast.makeText(this, "Permission denied for: ${deniedPermissions.joinToString(", ")}. Please grant required permissions.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {


            data.data?.let { uri ->
                profileImageView.setImageURI(uri)

                userRepository.uploadProfileImage(uri, onSuccess = { imageUrl ->

                    userRepository.saveProfileImageUrl(imageUrl, onSuccess = {
                        Toast.makeText(this, "Profile image updated successfully.", Toast.LENGTH_SHORT).show()
                    }, onFailure = { e ->
                        Toast.makeText(this, "Failed to save profile image URL: ${e.message}", Toast.LENGTH_LONG).show()
                    })
                }, onFailure = { e ->
                    Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
                })
            }
        }
    }


    private fun updateUserNameAndEmail(userId: String) {
        userRepository.getUserName(userId, object : UserRepository.UserNameCallback {
            override fun onUserNameReceived(userName: String?) {
                txtName.text = userName ?: "Name not found"
            }
        })

        userRepository.getEmail(userId, object : UserRepository.getEmailCallback {
            override fun onEmailReceived(Email: String?) {
                txtEmail.text = Email ?: "Email not found"
            }
        })
    }


    private fun fetchAndDisplayProfileImage(userId: String) {
        userRepository.fetchProfileImageUrl(userId, onSuccess = { imageUrl ->
            if (imageUrl != null) {
                Glide.with(this)
                    .load(imageUrl)
                    .into(profileImageView)
            } else {
                profileImageView.setImageResource(R.drawable.default_avatar)
            }
        }, onFailure = { exception ->
            Toast.makeText(this, "Failed to fetch profile image: ${exception.message}", Toast.LENGTH_LONG).show()
        })
    }

}
