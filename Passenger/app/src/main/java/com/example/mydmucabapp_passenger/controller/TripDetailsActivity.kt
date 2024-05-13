package com.example.mydmucabapp_passenger.controller

import DriverDetailsAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.example.mydmucabapp_passenger.databinding.ActivityTripDetailsBinding
import com.example.mydmucabapp_passenger.helpers.SharedTripService
import com.example.mydmucabapp_passenger.model.DataClass.RideRequest
import com.example.mydmucabapp_passenger.model.DataClass.Trips
import com.example.mydmucabapp_passenger.model.DataHandling.TripsRepository
import com.example.mydmucabapp_passenger.model.DataHandling.UserRepository
import com.example.mydmucabapp_passenger.routeMatching.MatchHandler
import com.example.mydmucabapp_passenger.utils.FCMSend
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
    private var pId = ""
    private var pStart: GeoPoint? = null
    private var pEnd: GeoPoint? = null
    private var pRoute = ""
    private var pTime: Timestamp? = null
    private var ddId = ""
    private var dRideId = ""
    private lateinit var routesMatching: MatchHandler
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
        routesMatching = MatchHandler(tripsRepository)

        FirebaseFirestore.getInstance().collection("scheduledPassengerTrips").document(docId).get().addOnSuccessListener {
            if (it != null){
                ddId = it.getString("driverId").toString()
                dRideId = it.getString("driverRideId").toString()
                pId = it.get("passengerId").toString()
                pTime = it.getTimestamp("scheduledTime")
                pStart = it.getGeoPoint("startLocation")
                pEnd = it.getGeoPoint("endLocation")
                pRoute = it.getString("route").toString()
                val requestStatus: String = it.getString("requestStatus").toString()
                if (requestStatus == "pending") {
                    findRides(pId, pTime, pStart, pEnd, pRoute)
                }else if ((requestStatus == "requested") && !ddId.isNullOrEmpty() && !dRideId.isNullOrEmpty()) {
                    findAcceptedRide(dRideId, isRequested = true)
                }else if ((requestStatus == "accepted") && !ddId.isNullOrEmpty() && !dRideId.isNullOrEmpty()) {
                    findAcceptedRide(dRideId, isAccepted = true)
                }
            }else{
                Toast.makeText(this, "No Trips Found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun findRides(passengerId: String, scheduledTime: Timestamp?, originGeoPoint: GeoPoint?, destinationGeoPoint: GeoPoint?, encodedPolyline: String?) {
        showProgressBar()

        CoroutineScope(Dispatchers.IO).launch {
            try {

                if (scheduledTime != null && originGeoPoint != null && destinationGeoPoint != null && encodedPolyline != null) {
                    val tripData = passengerId?.let {
                        Trips(
                            passengerId = it,
                            startLocation = originGeoPoint,
                            endLocation = destinationGeoPoint,
                            route = encodedPolyline,
                            scheduledTime = scheduledTime
                        )
                    }

                    tripData?.let { SharedTripService.setTripDetails(it) }

                    routesMatching.matchDriverWithPassenger(originGeoPoint, destinationGeoPoint, collectionName = "scheduledDriverTrips") { success, drivers ->

                        CoroutineScope(Dispatchers. IO).launch {
                            if (drivers != null) {
                                if (success && drivers.isNotEmpty()) {

                                    val rides = usersRepository.fetchRides(drivers, "scheduledDriverTrips")

                                    val ridesArrayList = ArrayList(rides)

                                    withContext(Dispatchers.Main){
                                        setListToRv(ridesArrayList)
                                        hideProgressBar()
                                    }


                                }


                            }

                            else {
                                showToast("No drivers found.")
                            }
                        }
                    }
                } else {
                    showToast("Failed to process locations or encode polyline.")
                }
            } catch (e: Exception) {
                showToast("An error occurred: ${e.localizedMessage}")
            } finally {
                hideProgressBar() // Ensure progress bar is hidden regardless of success or failure
            }
        }
    }

    private fun findAcceptedRide(rideId: String, isRequested: Boolean = false, isAccepted: Boolean = false){
        CoroutineScope(Dispatchers. IO).launch {
            val rides = usersRepository.fetchRides(listOf(rideId), "scheduledDriverTrips")

            val ridesArrayList = ArrayList(rides)

            withContext(Dispatchers.Main){
                setListToRv(ridesArrayList, isRequested, isAccepted)
                hideProgressBar()
            }
        }
    }

    private fun showProgressBar() = CoroutineScope(Dispatchers.Main).launch {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() = CoroutineScope(Dispatchers.Main).launch {
        binding.progressBar.visibility = View.GONE
    }

    private fun showToast(message: String) = CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(this@TripDetailsActivity, message, Toast.LENGTH_SHORT).show()
    }

    private fun setListToRv(rides: ArrayList<RideRequest>, isRequested: Boolean = false, isAccepted: Boolean = false){
        val adapter = DriverDetailsAdapter(rides, isRequested, isAccepted)
        binding.availableRidesRecycler.adapter = adapter

        adapter.onItemClick = { ride ->

            val db = FirebaseFirestore.getInstance()
            val tripMap: Map<String, Any> = hashMapOf(
                "driverRideId" to ride.documentId,
                "driverId" to ride.trip.driverId,
                "requestStatus" to "requested"
            )

            db.collection("scheduledPassengerTrips").document(docId)
                .update(tripMap)
                .addOnSuccessListener {
                    FCMSend().pushNotification(
                        this,
                        "/topics/${ride.user.userId}",
                        "Notification",
                        "A passenger requested you for a seat in your scheduled trip."
                    ) {}
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }

        }

        adapter.onContinueClick = { ride ->

            if (ride.trip.scheduledTime != null){
                val rDate = pTime?.toDate()
                val calendarToday = Calendar.getInstance()
                val currentDate = calendarToday.time
                val calendarDateToCheck = Calendar.getInstance()
                calendarDateToCheck.time = rDate

                val isToday = rDate!!.after(currentDate) && calendarToday.get(Calendar.YEAR) == calendarDateToCheck.get(
                    Calendar.YEAR) &&
                        calendarToday.get(Calendar.MONTH) == calendarDateToCheck.get(Calendar.MONTH) &&
                        calendarToday.get(Calendar.DAY_OF_MONTH) == calendarDateToCheck.get(Calendar.DAY_OF_MONTH)

                if (isToday) {
                    SharedTripService.clearTripDetails()
                    SharedTripService.setDocumentId(pId)
                    SharedTripService.setDriverId(ride.trip.driverId)
                    SharedTripService.setRideDetails(ride)

                    val tripMap = hashMapOf<String, Any?>(
                        "passengerId" to pId,
                        "startLocation" to pStart,
                        "endLocation" to pEnd,
                        "route" to pRoute,
                        "driverId" to ride.trip.driverId,
                        "requestStatus" to "accepted",
                    )
                    pTime?.let {
                        tripMap["scheduledTime"] = it
                    }

                    try {
                        FirebaseFirestore.getInstance().collection("immediateRequestedTrips").document(pId).set(tripMap).addOnSuccessListener {
                            FCMSend().pushNotification(
                                this,
                                "/topics/${ride.user.userId}",
                                "Notification",
                                "Passengers are waiting, start your scheduled trip."
                            ) {}
                            finish()
                        }
                        val intent = Intent(this, RideActive::class.java)
                        startActivity(intent)
                        finishAffinity()
                    }catch (e: Exception){
                        showToast(e.message.toString())
                    }
                }else{
                    showToast("Scheduled Time is not today!")
                }

            }
        }
    }
}