package com.example.mydmucabapp_driver.controller

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView

import com.example.mydmucabapp_driver.R

class LoginRegister : AppCompatActivity() {


    private var imageView: ImageView? = null
    private lateinit var buttonRegister: Button
    private lateinit var buttonLogin: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_register)


        buttonRegister = findViewById<Button>(R.id.btnRegister)
        buttonLogin = findViewById<Button>(R.id.btnLogin)

        val carLogo = findViewById<ImageView>(R.id.car_logo)
        val carAnimation = AnimationUtils.loadAnimation(this, R.anim.disapper_and_appear)

        carLogo.startAnimation(carAnimation)

        imageView?.startAnimation(carAnimation)

        buttonRegister.setOnClickListener(){

            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }

        buttonLogin.setOnClickListener(){

            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}