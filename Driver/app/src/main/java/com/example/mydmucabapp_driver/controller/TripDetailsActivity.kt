package com.example.mydmucabapp_driver.controller

import RideRequestsAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.mydmucabapp_driver.databinding.ActivityTripDetailsBinding
import com.example.mydmucabapp_driver.helpers.SharedTripService
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.example.mydmucabapp_driver.model.DataHandling.TripsRepository
import com.example.mydmucabapp_driver.model.DataHandling.UserRepository
import com.example.mydmucabapp_driver.utils.FCMSend
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class TripDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailsBinding
    private var docId = ""
    private var tripPostedTime: Timestamp? = null
    private var driverStartLocation: GeoPoint? = null
    private lateinit var tripsRepository: TripsRepository
    private lateinit var usersRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        docId = intent.getStringExtra("id").toString()
        binding.imgBack.setOnClickListener { finish() }

        tripsRepository = TripsRepository()
        usersRepository = UserRepository()

        FirebaseFirestore.getInstance().collection("scheduledDriverTrips").document(docId).get().addOnSuccessListener {
            if (it != null) {
                tripPostedTime = it.getTimestamp("scheduledTime")
                driverStartLocation = it.getGeoPoint("startLocation")
                if (tripPostedTime != null){
                    val rDate = tripPostedTime!!.toDate()
                    val calendarToday = Calendar.getInstance()
                    val currentDate = calendarToday.time
                    val calendarDateToCheck = Calendar.getInstance()
                    calendarDateToCheck.time = rDate

                    val isToday = rDate.after(currentDate) && calendarToday.get(Calendar.YEAR) == calendarDateToCheck.get(
                        Calendar.YEAR) &&
                            calendarToday.get(Calendar.MONTH) == calendarDateToCheck.get(Calendar.MONTH) &&
                            calendarToday.get(Calendar.DAY_OF_MONTH) == calendarDateToCheck.get(Calendar.DAY_OF_MONTH)

                    if (isToday) {
                        binding.btnStart.visibility = View.VISIBLE
                    }

                }
            }
        }

        binding.btnStart.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                FirebaseFirestore.getInstance().collection("scheduledPassengerTrips").get().addOnSuccessListener {
                    if (it != null && it.documents.size > 0){
                        val list = mutableListOf<String>()
                        SharedTripService.setPostedTripsDocumentId(docId)
                        for (d in it.documents){
                            val id = d.getString("id").toString()
                            val driverRideId = d.getString("driverRideId").toString()
                            val passengerId = d.getString("passengerId").toString()
                            val requestStatus = d.getString("requestStatus").toString()
                            if (driverRideId == docId && requestStatus == "accepted"){
                                SharedTripService.setAcceptedTripsDocumentIds(passengerId)
                                list.add(id)
                            }
                        }
                        list.forEach {
                            FirebaseFirestore.getInstance().collection("scheduledPassengerTrips").document(it).delete()
                        }
                        startRide()
//                        CoroutineScope(Dispatchers.Main).launch {
//                            val rList = tripsRepository.fetchRideRequests(list)
//                            rList.forEach {
//                                SharedTripService.addAcceptedTrip(it)
//                            }
//                        }
                    }
                }
            }
        }

        FirebaseFirestore.getInstance().collection("scheduledDriverTrips").document(docId).addSnapshotListener { it, error ->
            if (error != null){
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (it != null){
                binding.progressBar.visibility = View.VISIBLE
                val availableSeats = it.get("availableSeats").toString()
                if (!availableSeats.equals("null") && availableSeats.toInt() > 0){
                    finRequests()
                }else{
                    binding.requestedRidesRecycler.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE
                }
                binding.txtAvailableSeats.text = "Available Seats: "+availableSeats
            }else{
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "No Requests Available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun finRequests() {
        val db = FirebaseFirestore.getInstance()
        db.collection("scheduledPassengerTrips")
            .whereEqualTo("driverRideId", docId)
            .whereEqualTo("requestStatus", "requested")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val documentIds = snapshots?.documents?.map { it.id } ?: listOf()
                if (documentIds.isNotEmpty()){
                    binding.requestedRidesRecycler.visibility = View.VISIBLE
                    CoroutineScope(Dispatchers.Main).launch {
                        val rideRequests = tripsRepository.fetchRideRequests(documentIds, "scheduledPassengerTrips")
                        setListToRv(rideRequests)
                        binding.progressBar.visibility = View.GONE
                    }
                }else{
                    binding.requestedRidesRecycler.visibility = View.GONE
                    binding.progressBar.visibility = View.GONE
                }
            }
    }

    private fun setListToRv(rides: List<RideRequest>){
        val adapter = RideRequestsAdapter(rides)
        binding.requestedRidesRecycler.adapter = adapter

        adapter.onAcceptClick = { ride ->

            val db = FirebaseFirestore.getInstance()
            val tripMap: Map<String, Any> = hashMapOf(
                "requestStatus" to "accepted"
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    tripsRepository.decrementAvailableSeats(docId, "scheduledDriverTrips")
                }catch (e: Exception){}
            }

            db.collection("scheduledPassengerTrips").document(ride.documentId)
                .update(tripMap)
                .addOnSuccessListener {
                    FCMSend().pushNotification(
                        this,
                        "/topics/${ride.user.userId}",
                        "Notification",
                        "Your scheduled trip request is accepted"
                    ) {}
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }

        }
        adapter.onRejectClick = { ride ->
            val db = FirebaseFirestore.getInstance()
            val tripMap: Map<String, Any> = hashMapOf(
                "driverId" to "",
                "driverRideId" to "",
                "requestStatus" to "pending",
            )

            db.collection("scheduledPassengerTrips").document(ride.documentId)
                .update(tripMap)
                .addOnSuccessListener {
                    FCMSend().pushNotification(
                        this,
                        "/topics/${ride.user.userId}",
                        "Notification",
                        "Your scheduled trip request is rejected"
                    ) {}
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun startRide() {
        val db = FirebaseFirestore.getInstance()
        val acceptedRideRequestIds = SharedTripService.getAcceptedTripsDocumentIds()
        val postedTripDocumentId = SharedTripService.getPostedTripsDocumentId()
        val driverId = tripsRepository.getDriverId()

        if (postedTripDocumentId.isEmpty()) {
            Log.e("TripFlow", "No posted trip document ID found.")
            return
        }

        if (acceptedRideRequestIds.isEmpty()) {
            Toast.makeText(this, "No ride request has been accepted. Cannot start the trip.", Toast.LENGTH_SHORT).show()
            Log.e("TripFlow", "No ride request has been accepted.")
            return
        }


        lifecycleScope.launch {
            try {
                tripsRepository.updateAcceptedRideRequestsStatus(acceptedRideRequestIds, status = "active")
                val rideRequests = tripsRepository.fetchRideRequests(acceptedRideRequestIds)
                rideRequests.forEach {
                    SharedTripService.addAcceptedTrip(it)
                }
                val pickupLocations = rideRequests.map { it.trip.startLocation }
                val dropOffLocations = rideRequests.map { it.trip.endLocation }
                val passengerIds = rideRequests.map { it.user.userId }
                val passengersCount = rideRequests.size
                val tripStartTime = Timestamp.now()

//                val postedTripDetails = SharedTripService.getTripDetails()
                if (driverStartLocation != null && tripPostedTime != null) {
//                    val driverStartLocation = postedTripDetails.startLocation
//                    val tripPostedTime = postedTripDetails.scheduledTime

                    val rideData = mapOf(
                        "driverId" to driverId,
                        "passengerIds" to passengerIds,
                        "driverStartLocation" to driverStartLocation,
                        "pickupLocations" to pickupLocations,
                        "dropOffLocations" to dropOffLocations,
                        "tripStartTime" to tripStartTime,
                        "tripPostedTime" to tripPostedTime,
                        "passengersCount" to passengersCount,
                        "tripStatus" to "active"
                    )

                    withContext(Dispatchers.IO){
                        tripsRepository.addToRidesCollection(postedTripDocumentId, rideData)
                        db.collection("scheduledDriverTrips").document(postedTripDocumentId).delete()
                    }

                    withContext(Dispatchers.Main) {
                        SharedTripService.clearPostedTripsDocumentId()
                        val intent = Intent(this@TripDetailsActivity, ActiveRideActivity::class.java)
                        intent.putExtra("currentRideTime",tripStartTime.toString())
                        startActivity(intent)
                        finishAffinity()
                        Log.d("TripFlow", "Ride has been successfully started and moved to 'rides' collection.")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("TripFlow", "Failed to retrieve posted trip details from local storage.")
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TripDetailsActivity, "${exception.message}, An error occured, unable to start ride at the moment", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}