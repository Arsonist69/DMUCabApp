package com.example.mydmucabapp_passenger.controller

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import com.example.mydmucabapp_passenger.R
import com.google.firebase.auth.FirebaseAuth

class HomePageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_homepage)

        val carLogo = findViewById<ImageView>(R.id.logoCar)
        val carAnimation = AnimationUtils.loadAnimation(this, R.anim.appear_and_disapper_car)
        carLogo.startAnimation(carAnimation)

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                val dashboardIntent = Intent(this, DashBoardActivity::class.java)
                startActivity(dashboardIntent)
                finish()
            }, 3200)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                val loginIntent = Intent(this, LoginRegister::class.java)
                startActivity(loginIntent)
                finish()
            }, 3200)
        }
    }
}
