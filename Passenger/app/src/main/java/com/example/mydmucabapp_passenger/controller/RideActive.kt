package com.example.mydmucabapp_passenger.controller

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.mydmucabapp_passenger.adopters.MessagesAdapter
import com.example.mydmucabapp_passenger.databinding.ActivityRideActiveBinding
import com.example.mydmucabapp_passenger.helpers.SharedTripService
import com.example.mydmucabapp_passenger.model.DataClass.ChatMessage
import com.example.mydmucabapp_passenger.model.DataClass.RideRequest
import com.example.mydmucabapp_passenger.model.DataHandling.MessageHandler
import com.example.mydmucabapp_passenger.model.DataHandling.TripsRepository
import com.example.mydmucabapp_passenger.model.DataHandling.UserRepository
import com.google.firebase.firestore.GeoPoint
import java.util.Locale

class RideActive : AppCompatActivity() {

    private lateinit var binding: ActivityRideActiveBinding
    private val tripsRepository = TripsRepository()
    private val messageHandler = MessageHandler()
    private val userRepository = UserRepository()
    private var userId = userRepository.getPassengerId()

     private var ride = SharedTripService.getRideDetails()
    private var driverId = SharedTripService.getDriverId()

    private lateinit var adapterMessages: MessagesAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideActiveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        listenForTripDeletion()

        ride?.let {
            Glide.with(this).load(it.user.profileImageUrl)
                .into(binding.ProfilePicture)
            binding.driverName.text = it.user.name
            binding.StartLocation.text = it.trip.startLocation.let { loc -> getAddressFromLocation(loc) }
            binding.textViewCarReg.text= it.user.vehicleRegistration
            binding.textViewCarColor.text = it.user.vehicleColor
            binding.textViewCarModel.text = it.user.vehicleModel
            binding.textViewPhoneNumber.text = it.user.phone
        } ?: Toast.makeText(this, "Ride details not available", Toast.LENGTH_SHORT).show()

        setupMessagesRecyclerView()

        binding.chatIcon.setOnClickListener {
            toggleLinearLayoutVisibility()
            ride?.let { it1 -> setupChat(it1) }
        }
        binding.sendButton.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString()
                    sendMessage(messageText)
                    binding.editTextMessage.text.clear()
                }
            }







    private fun setupMessagesRecyclerView() {
        adapterMessages =  MessagesAdapter(mutableListOf(), userId!!)
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapterMessages
    }



    private fun setupChat(acceptedRide: RideRequest) {


         val cachedChatId = userId?.let { getChatIdFromCache(driverId, it) }
        if (cachedChatId != null) {
            loadChatMessages(cachedChatId)
        } else {
            messageHandler.checkForExistingChat(listOf(driverId, userId)) { chatId ->
                if (chatId != null) {
                    userId?.let { cacheChatId(driverId, it, chatId) }
                    loadChatMessages(chatId)
                } else {
                    messageHandler.startChat(listOf(driverId, userId)) { newChatId ->
                        userId?.let { cacheChatId(driverId, it, newChatId) }
                        loadChatMessages(newChatId)
                    }
                }
            }
        }
    }

    private fun loadChatMessages(chatId: String) {
        messageHandler.fetchChatMessages(chatId) { messages ->
            runOnUiThread {
                adapterMessages.updateMessages(messages)
                adapterMessages.notifyDataSetChanged()
            }
        }
    }


    private fun sendMessage(messageText: String) {




        val chatId = userId?.let { getChatIdFromCache(driverId, it) }
        if (chatId == null) {
            Toast.makeText(this, "Chat not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val newMessage = userId?.let {
            ChatMessage(
                senderId = it,
                receiverId = driverId,
                message = messageText,
                timestamp = System.currentTimeMillis()
            )
        }

        if (newMessage != null) {
            messageHandler.sendMessage(chatId, newMessage, {
                Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
            }, { exception ->
                Toast.makeText(this, "Failed to send message: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
            })
        }
    }

    private fun getAddressFromLocation(location: GeoPoint): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
        } catch (e: Exception) {
            "Unknown Location"
        }
    }

    fun toggleLinearLayoutVisibility() {

       var expandedLayout = binding.layoutExpanded

        if (expandedLayout.visibility == View.GONE) {
            expandedLayout.visibility = View.VISIBLE
        } else {
            expandedLayout.visibility = View.GONE
        }
    }

    private fun getChatIdFromCache(driverId: String, passengerId: String): String? {
        val sharedPreferences = getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("$driverId-$passengerId", null)
    }

    private fun cacheChatId(driverId: String, passengerId: String, chatId: String) {
        val sharedPreferences = getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("$driverId-$passengerId", chatId)
            apply()
        }

    }

    private fun clearCachedData() {
        val sharedPreferences = getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
    }

    private fun listenForTripDeletion() {
        userId?.let {
            tripsRepository.listenForPassengerTripCompletion(it) { isDeleted ->
                if (isDeleted) {
                    navigateToMainDashboard()
                    clearCachedData()
                }

            }
        }
    }
    private fun navigateToMainDashboard() {
        val intent = Intent(this, DashBoardActivity::class.java)
        startActivity(intent)
        finish()
    }

}