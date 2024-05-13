package com.example.mydmucabapp_driver.controller

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mydmucabapp_driver.R
import com.example.mydmucabapp_driver.databinding.ActivityScheduleTripBinding
import com.example.mydmucabapp_driver.helpers.RouteRepository
import com.example.mydmucabapp_driver.model.DataClass.Trips
import com.github.florent37.singledateandtimepicker.SingleDateAndTimePicker
import com.github.florent37.singledateandtimepicker.dialog.SingleDateAndTimePickerDialog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class ScheduleTripActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleTripBinding
    private var selectedDate: Date? = null
    private lateinit var routesRepository: RouteRepository
    private var driverId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleTripBinding.inflate(layoutInflater)
        setContentView(binding.root)

        driverId = FirebaseAuth.getInstance().currentUser?.uid!!

        routesRepository = RouteRepository(RetrofitClient.googleMapsApiService)

        binding.imgBack.setOnClickListener { finish() }
        binding.txtDate.setOnClickListener {
            SingleDateAndTimePickerDialog.Builder(this) //.bottomSheet()
                .mustBeOnFuture().minutesStep(1)
                .title("Select Date")
                .listener(object : SingleDateAndTimePickerDialog.Listener {
                    override fun onDateSelected(date: Date?) {
                        val currentDate = Calendar.getInstance().time
                        if (date == null || date.before(currentDate)){
                            Toast.makeText(this@ScheduleTripActivity, "Select Future Date!", Toast.LENGTH_SHORT).show()
                        }else{
                            selectedDate = date
                            binding.txtDate.text = SimpleDateFormat("MMMM dd, yyyy hh:mm a z").format(date)
                        }
                    }
                }).display()
        }
        binding.reserveButton.setOnClickListener {
            val originText = binding.txtPickUpLocation.text.toString().trim()
            val destinationText = binding.txtDestinationLocation.text.toString().trim()
            val availableSeats = binding.edtAvailableSeats.text.toString().trim()
            if (originText.isEmpty()){
                Toast.makeText(this, "Enter Pickup Location", Toast.LENGTH_SHORT).show()
            }else if (destinationText.isEmpty()){
                Toast.makeText(this, "Enter Destination", Toast.LENGTH_SHORT).show()
            }else if (availableSeats.isEmpty() || availableSeats == "0"){
                Toast.makeText(this, "Enter Available Seats", Toast.LENGTH_SHORT).show()
            }else if (selectedDate == null){
                Toast.makeText(this, "Select Date", Toast.LENGTH_SHORT).show()
            }else{
                CoroutineScope(Dispatchers.Main).launch {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.reserveButton.visibility = View.GONE
                    try {
                        val originGeoPoint = routesRepository.geocodeLocation(originText, this@ScheduleTripActivity)
                        val destinationGeoPoint =
                            routesRepository.geocodeLocation(destinationText, this@ScheduleTripActivity)
                        val encodedPolyline = routesRepository.fetchEncodedPolyline(
                            originText, destinationText, getString(
                                R.string.google_maps_routes_key
                            )
                        )
                        if (originGeoPoint != null && destinationGeoPoint != null && encodedPolyline != null) {
                            val parentRefId = FirebaseFirestore.getInstance().collection("scheduledDriverTrips").document().id
                            val tripMap = hashMapOf<String, Any>(
                                "id" to parentRefId,
                                "driverId" to driverId,
                                "availableSeats" to availableSeats.toInt(),
                                "startLocation" to originGeoPoint,
                                "endLocation" to destinationGeoPoint,
                                "route" to encodedPolyline,
                                "scheduledTime" to Timestamp(selectedDate!!)
                            )
                            FirebaseFirestore.getInstance().collection("scheduledDriverTrips").document(parentRefId).set(tripMap).addOnSuccessListener {
                                binding.progressBar.visibility = View.GONE
                                binding.reserveButton.visibility = View.VISIBLE
                                Toast.makeText(this@ScheduleTripActivity, "Trip Scheduled", Toast.LENGTH_SHORT).show()
                                finish()
                            }.addOnFailureListener {
                                binding.progressBar.visibility = View.GONE
                                binding.reserveButton.visibility = View.VISIBLE
                                Toast.makeText(this@ScheduleTripActivity, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }else{
                            binding.progressBar.visibility = View.GONE
                            binding.reserveButton.visibility = View.VISIBLE
                            Toast.makeText(this@ScheduleTripActivity, "Invalid Locations", Toast.LENGTH_SHORT).show()
                        }
                    }catch (e: Exception){
                        binding.progressBar.visibility = View.GONE
                        binding.reserveButton.visibility = View.VISIBLE
                        Toast.makeText(this@ScheduleTripActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}