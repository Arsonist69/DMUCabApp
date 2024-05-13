package com.example.mydmucabapp_passenger.fragment


import android.Manifest
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.location.Geocoder
import android.widget.Button
import android.widget.Toast
import java.io.IOException
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mydmucabapp_passenger.model.DataHandling.TripsRepository
import com.example.mydmucabapp_passenger.R
import com.example.mydmucabapp_passenger.controller.ScheduleTripActivity
import com.example.mydmucabapp_passenger.helpers.RouteCreation
import com.example.mydmucabapp_passenger.helpers.SharedTripService
import com.example.mydmucabapp_passenger.model.DataClass.Trips
import com.example.mydmucabapp_passenger.model.DataHandling.UserRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale


class MainDashBoard : Fragment(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationPermissionGranted = false
    private lateinit var txtPickUpLocation : EditText
    private lateinit var txtDestinationLocation : EditText
    private lateinit var btnFindPickUpLocation: Button
    private lateinit var btnFindDestinationLocation: Button
    private lateinit var btnFindRide: Button
    private lateinit var btnSchedule: Button
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var progressBarLogin: ProgressBar
    private lateinit var routesRepository: RouteCreation
    private lateinit var tripsRepository: TripsRepository
    private lateinit var users: UserRepository
    private lateinit var routesMatching: com.example.mydmucabapp_passenger.routeMatching.MatchHandler


    private val defaultLocation = LatLng(-33.8523341, 151.2106085)
    private var lastKnownLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        routesRepository = RouteCreation(RetrofitClient.googleMapsApiService)
        tripsRepository = TripsRepository()
        users = UserRepository()
        routesMatching =
            com.example.mydmucabapp_passenger.routeMatching.MatchHandler(tripsRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_main_dash_board, container, false)

        txtPickUpLocation = view.findViewById(R.id.txtPickUpLocation)
        txtDestinationLocation = view.findViewById(R.id.txtDestinationLocation)

        val linearLayoutBottomSheet = view.findViewById<LinearLayout>(R.id.linearLayoutBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(linearLayoutBottomSheet)

        bottomSheetBehavior.peekHeight = 200
        bottomSheetBehavior.isHideable = false

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        btnSchedule = view.findViewById(R.id.scheduleButton)
        btnSchedule.setOnClickListener{
            startActivity(Intent(requireContext(),ScheduleTripActivity::class.java))
        }
        btnFindPickUpLocation = view.findViewById(R.id.btnFindPickUpLocation)
        btnFindPickUpLocation.setOnClickListener{
            val pickupLocationText = txtPickUpLocation.text.toString()
            searchLocation(pickupLocationText)

        }
        btnFindDestinationLocation = view.findViewById(R.id.btnFindDestination)
        btnFindDestinationLocation.setOnClickListener{
            val destinationLocationText = txtDestinationLocation.text.toString()
            searchLocation(destinationLocationText)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val originLocationString = txtPickUpLocation.text.toString()
                    val apiKey = getString(R.string.google_maps_routes_key)

                    val encodedPolyline = routesRepository.fetchEncodedPolyline(originLocationString, destinationLocationText, apiKey)
                    if (encodedPolyline != null) {
                        val decodedPoints = routesRepository.decodePoly(encodedPolyline)
                        delay(1000)
                        withContext(Dispatchers.Main) {
                            drawRouteOnMap(decodedPoints)
                            val boundsBuilder = LatLngBounds.Builder()
                            for (point in decodedPoints) {
                                boundsBuilder.include(point)
                            }
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Failed to calculate route", Toast.LENGTH_SHORT).show()
                        }
                    }
                }  catch (e: Exception) {}
            }

        }

        progressBarLogin = view.findViewById(R.id.progressBarLogin)


        btnFindRide = view.findViewById(R.id.findRideButton)
        btnFindRide.setOnClickListener{
            findRide()
        }



        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pId = FirebaseAuth.getInstance().currentUser?.uid
        FirebaseFirestore.getInstance().collection("scheduledPassengerTrips").get().addOnSuccessListener {
            if (it != null && it.documents.isNotEmpty()){
                for (d in it.documents) {
                    val passengerId = d.get("passengerId").toString()
                    val requestStatus = d.getString("requestStatus").toString()
                    if (pId == passengerId && requestStatus == "accepted") {
                        val timestamp: Timestamp? = d.getTimestamp("scheduledTime")
                        if (timestamp != null){
                            val rDate = timestamp.toDate()
                            val calendarToday = Calendar.getInstance()
                            val currentDate = calendarToday.time
                            val calendarDateToCheck = Calendar.getInstance()
                            calendarDateToCheck.time = rDate

                            val isToday = rDate.after(currentDate) && calendarToday.get(Calendar.YEAR) == calendarDateToCheck.get(Calendar.YEAR) &&
                                    calendarToday.get(Calendar.MONTH) == calendarDateToCheck.get(Calendar.MONTH) &&
                                    calendarToday.get(Calendar.DAY_OF_MONTH) == calendarDateToCheck.get(Calendar.DAY_OF_MONTH)

                            if (isToday) {
                                AlertDialog.Builder(requireContext())
                                    .setTitle("Scheduled Trip Update")
                                    .setMessage("There is a scheduled trip of today, go and check!")
                                    .setPositiveButton("OK") { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                    .create()
                                    .show()
                                break
                            }

                        }
                    }
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        getLocationPermission()

        updateLocationUI()

        getDeviceLocation()
    }










    private fun findRide() {
        showProgressBar()

        val passengerId = FirebaseAuth.getInstance().currentUser?.uid
        var isCheckingForDrivers = true
        val searchDuration = 120_000L
        val searchInterval = 10_000L

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val originText = txtPickUpLocation.text.toString()
                val destinationText = txtDestinationLocation.text.toString()

                val originGeoPoint = routesRepository.geocodeLocation(originText, requireContext())
                val destinationGeoPoint = routesRepository.geocodeLocation(destinationText, requireContext())
                val encodedPolyline = routesRepository.fetchEncodedPolyline(originText, destinationText, getString(R.string.google_maps_routes_key))

                if (originGeoPoint != null && destinationGeoPoint != null && encodedPolyline != null) {
                    val tripData = passengerId?.let {
                        Trips(
                            passengerId = it,
                            startLocation = originGeoPoint,
                            endLocation = destinationGeoPoint,
                            route = encodedPolyline,
                            scheduledTime = null
                        )
                    }

                    tripData?.let { SharedTripService.setTripDetails(it) }

                    val startTime = System.currentTimeMillis()

                    while (isCheckingForDrivers && System.currentTimeMillis() - startTime < searchDuration) {
                        routesMatching.matchDriverWithPassenger(originGeoPoint, destinationGeoPoint) { success, drivers ->
                            isCheckingForDrivers = !success

                            CoroutineScope(Dispatchers. IO).launch {
                            if (drivers != null) {
                                if (success && drivers.isNotEmpty()) {

                                    val rides = users.fetchRides(drivers)
                                    val ridesArrayList = ArrayList(rides)
                                    isCheckingForDrivers = false

                                    withContext(Dispatchers.Main){
                                        val dialogFragment =
                                            DriverDetailsDialogFragment.newInstance(
                                                ridesArrayList
                                            )
                                        dialogFragment.show(
                                            parentFragmentManager,
                                            "DriverDetailsDialog"
                                        )
                                        hideProgressBar()
                                    }


                                }


                            }

                                else {
                                    showToast("No drivers found. Retrying...")

                                    if (System.currentTimeMillis() - startTime < searchDuration) {
                                        delay(searchInterval)
                                    }
                                }
                            }
                        }
                        if (isCheckingForDrivers) delay(searchInterval)
                    }
                } else {
                    showToast("Failed to process locations or encode polyline.")
                }
            } catch (e: Exception) {
                showToast("An error occurred: ${e.localizedMessage}")
            } finally {
                hideProgressBar()
            }
        }
    }



    private fun showProgressBar() = CoroutineScope(Dispatchers.Main).launch {
        progressBarLogin.visibility = View.VISIBLE
    }

    private fun hideProgressBar() = CoroutineScope(Dispatchers.Main).launch {
        progressBarLogin.visibility = View.GONE
    }

    private fun showToast(message: String) = CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }





    private fun getLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        locationPermissionGranted = false
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionGranted = true
                }
            }
        }
        updateLocationUI()
    }

    private fun updateLocationUI() {
        try {
            if (locationPermissionGranted) {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
            } else {
                map.isMyLocationEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
                lastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {

        }
    }

    private fun getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(requireActivity()) { task ->
                    if (task.isSuccessful && task.result != null) {
                        lastKnownLocation = task.result
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        val addresses = geocoder.getFromLocation(
                            lastKnownLocation!!.latitude,
                            lastKnownLocation!!.longitude,
                            1
                        )
                        if (addresses != null) {
                            if (addresses.isNotEmpty()) {
                                val address = addresses[0]?.getAddressLine(0)
                                txtPickUpLocation.setText(address)
                            }
                        }
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude), 15f))
                    } else {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
                        map.uiSettings.isMyLocationButtonEnabled = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        } catch (e: IOException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    private fun searchLocation(locationName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val geocoder = Geocoder(requireContext())
            try {
                val addressList = geocoder.getFromLocationName(locationName, 1)
                if (!addressList.isNullOrEmpty()) {
                    val address = addressList[0]
                    val latLng = LatLng(address.latitude, address.longitude)
                    withContext(Dispatchers.Main) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        map.addMarker(MarkerOptions().position(latLng).title(locationName))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Location not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun drawRouteOnMap(polylinePoints: List<LatLng>) {
        CoroutineScope(Dispatchers.Main).launch {
            if (polylinePoints.isNotEmpty()) {
                val polylineOptions = PolylineOptions()
                    .addAll(polylinePoints)
                    .width(10f)
                    .color(Color.BLUE)
                    .geodesic(true) 
                map.addPolyline(polylineOptions)
            } else {
                Toast.makeText(requireContext(), "No route found", Toast.LENGTH_SHORT).show()
            }
        }
    }





    companion object {
        private const val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }
}
