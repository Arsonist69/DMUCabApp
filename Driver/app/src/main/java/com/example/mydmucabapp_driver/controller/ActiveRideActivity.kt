package com.example.mydmucabapp_driver.controller

import AcceptedRidesAdapter
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mydmucabapp_driver.adopters.MessagesAdapter
import com.example.mydmucabapp_driver.databinding.ActivityActiveRideBinding
import com.example.mydmucabapp_driver.helpers.SharedTripService
import com.example.mydmucabapp_driver.model.DataClass.ChatMessage
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.example.mydmucabapp_driver.model.DataHandling.MessageHandler
import com.example.mydmucabapp_driver.model.DataHandling.TripsRepository
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore


class ActiveRideActivity : AppCompatActivity() {
    private var currentRideRequest: RideRequest? = null

    private lateinit var _binding: ActivityActiveRideBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private val tripsRepository = TripsRepository()
    private lateinit var adapterMessages: MessagesAdapter
    private val messageHandler = MessageHandler()
    private lateinit var adapter: AcceptedRidesAdapter
    private lateinit var acceptedRequests: MutableList<RideRequest>
    private lateinit var sortedRequests: MutableList<RideRequest>
    private var driverId = tripsRepository.getDriverId()
    private var currentRideTime = ""


    private val binding: ActivityActiveRideBinding by lazy {
        ActivityActiveRideBinding.inflate(layoutInflater)
    }


    override fun onDestroy() {
        super.onDestroy()
        clearCachedData()
    }


        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        currentRideTime = intent.getStringExtra("currentRideTime").toString()
        setupBottomSheet()


       acceptedRequests = SharedTripService.getAcceptedTrips().toMutableList()
        val driverLocation = SharedTripService.getLastKnownLocation()

            sortedRequests = driverLocation?.let { getSortedByProximity(acceptedRequests, it) } as MutableList<RideRequest>
        setupRidesAdapter(sortedRequests)
        adapterMessages = MessagesAdapter(mutableListOf(), driverId!!)
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMessages.adapter = adapterMessages

            binding.btnComplete.setOnClickListener{
                completeRides()
            }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.linearLayoutBottomSheet)
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.isFitToContents = false
        bottomSheetBehavior.expandedOffset = 0

        binding.btnCloseChat.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }




    binding.sendButton.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString()
            if (messageText.isNotEmpty()) {
                currentRideRequest?.let {
                    sendMessage(messageText, it)
                    binding.editTextMessage.text.clear()
                }
            }
        }

    }



    private fun setupRidesAdapter(rides: List<RideRequest>?) {
        val mutableRides = rides?.toMutableList() ?: mutableListOf()
        adapter = AcceptedRidesAdapter(
            mutableRides,
            { ride -> dropOffPassenger(ride) { removedRide ->
                mutableRides.remove(removedRide)
                adapter.notifyDataSetChanged()
            }},
            this::setupChat
        )

        binding.recyclerViewActiveRides.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewActiveRides.adapter = adapter
    }





    private fun getSortedByProximity(
        rideRequests: List<RideRequest>,
        driverLocation: Location
    ): List<RideRequest> {
        return rideRequests.sortedBy { request ->
            val location = Location("").apply {
                latitude = request.trip.startLocation.latitude
                longitude = request.trip.startLocation.longitude
            }
            driverLocation.distanceTo(location)
        }
    }









    private fun sendMessage(messageText: String, ride: RideRequest) {

        Toast.makeText(this, "messaging", Toast.LENGTH_SHORT).show()
        val passengerId = ride.user.userId
        val driverId = this.driverId ?: return


        val chatId = getChatIdFromCache(driverId, passengerId)
        if (chatId == null) {
            Toast.makeText(this, "Chat not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        val newMessage = ChatMessage(
            senderId = driverId,
            receiverId = passengerId,
            message = messageText,
            timestamp = System.currentTimeMillis()
        )

        messageHandler.sendMessage(chatId, newMessage, {
            Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
        }, { exception ->
            Toast.makeText(this, "Failed to send message: ${exception.localizedMessage}", Toast.LENGTH_LONG).show()
        })
    }






    private fun setupChat(acceptedRide: RideRequest) {
        currentRideRequest=acceptedRide
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        val passengerId = acceptedRide.trip.passengerId

        val cachedChatId = driverId?.let { getChatIdFromCache(it, passengerId) }
        if (cachedChatId != null) {
            loadChatMessages(cachedChatId)
        } else {
            messageHandler.checkForExistingChat(listOf(driverId, passengerId)) { chatId ->
                if (chatId != null) {
                    driverId?.let { cacheChatId(it, passengerId, chatId) }
                    loadChatMessages(chatId)
                } else {
                    messageHandler.startChat(listOf(driverId, passengerId)) { newChatId ->
                        driverId?.let { cacheChatId(it, passengerId, newChatId) }
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


    private fun dropOffPassenger(acceptedRide: RideRequest, onRideDroppedOff: (RideRequest) -> Unit) {
        val rideId = acceptedRide.documentId
        val immediateRequestedRideRef = tripsRepository.getImmediateRequestedRideReference(rideId)
        val rideRef = FirebaseFirestore.getInstance().collection("ridesP").document(rideId)

        immediateRequestedRideRef.firestore.runTransaction { transaction ->
            val newDoc = mapOf(
                "driverId" to driverId,
                "passengerId" to acceptedRide.trip.passengerId,
                "startLocation" to acceptedRide.trip.startLocation,
                "dropOffLocation" to acceptedRide.trip.endLocation,
                "startTime" to acceptedRide.trip.scheduledTime,
                "dropOffTime" to Timestamp.now(),
                "amount" to "",
                "status" to "completed"
            )

            transaction.set(rideRef, newDoc)
            transaction.delete(immediateRequestedRideRef)

            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Trip completed for ${acceptedRide.user.name}", Toast.LENGTH_SHORT).show()
            acceptedRequests.remove(acceptedRide)
            adapter.notifyDataSetChanged()
            onRideDroppedOff(acceptedRide)
        }.addOnFailureListener { e ->
            Log.e("ActiveRideActivity", "Error completing ride: $rideId", e)
        }
    }



    fun completeRides(){

        if (acceptedRequests.isNotEmpty()) {
            Toast.makeText(this, "Cannot complete the trip while there are still customers remaining.", Toast.LENGTH_LONG).show()
            return
        }

        SharedTripService.clearPostedTripsDocumentId()
        SharedTripService.clearRequestedTripsDocumentIds()
        SharedTripService.clearAcceptedTrips()
        SharedTripService.clearAcceptedTripsDocumentIds()
        SharedTripService.clearLastKnownLocation()


        deleteAllChatsFromFirestore()
        updateRideStatusInRides()
        val dashboardIntent = Intent(this, DashBoardActivity::class.java)
        startActivity(dashboardIntent)
        finish()

    }

    private fun updateRideStatusInRides(){
        val ridesRef = tripsRepository.getRidesReference()
        ridesRef.get().addOnSuccessListener {
            if (!it.isEmpty()) {
                val list: List<DocumentSnapshot> = it.documents
                for (d in list) {
                    val dId = d.get("driverId").toString()
                    val rStartTime = d.get("tripStartTime").toString()
                    if (dId == driverId && rStartTime == currentRideTime) {
                        // Update the specific attribute of that document
                        val data = mapOf<String, String>("tripStatus" to "completed")
                        ridesRef.document(d.id).update(data)
                        break
                    }
                }
            }
        }
    }


    private fun deleteAllChatsFromFirestore() {
        val sharedPreferences = getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val chatIdsMap = sharedPreferences.all

        for (entry in chatIdsMap) {
            val chatId = entry.value as? String ?: continue
            tripsRepository.deleteChatFromFirestore(chatId)
        }
        sharedPreferences.edit().clear().apply()

    }



    }











