package com.example.mydmucabapp_driver.fragment


import android.Manifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.location.Geocoder
import android.widget.Button
import android.widget.Toast
import java.io.IOException
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mydmucabapp_driver.R
import RetrofitClient
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import androidx.appcompat.app.AlertDialog
import com.example.mydmucabapp_driver.controller.ScheduleTripActivity
import com.example.mydmucabapp_driver.helpers.RouteRepository
import com.example.mydmucabapp_driver.helpers.SharedTripService
import com.example.mydmucabapp_driver.model.DataClass.RideRequest
import com.example.mydmucabapp_driver.model.DataClass.Trips
import com.example.mydmucabapp_driver.model.DataHandling.TripsRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
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

class MainDashBoard : Fragment(), OnMapReadyCallback, TripsRepository.RideRequestListener, RideRequestsDialogFragment.RideRequestDialogListener {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationPermissionGranted = false
    private lateinit var txtPickupLocation: EditText
    private lateinit var txtDestinationLocation: EditText
    private lateinit var txtAvailableSeats: EditText
    private lateinit var btnFindDestination: Button
    private lateinit var btnFindRide: Button
    private lateinit var btnSchedule: Button
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    private lateinit var routesRepository: RouteRepository
    private lateinit var tripsRepository: TripsRepository
    private var isListeningForRequests: Boolean = true

    private val defaultLocation = LatLng(-33.8523341, 151.2106085)
    private var lastKnownLocation: Location? = null
    private val driverId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeLocationClient()
        initializeRepositories()

    }



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_main_dash_board, container, false)
        initializeViews(view)
        setupMapFragment()
        setupListeners()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dId = FirebaseAuth.getInstance().currentUser?.uid
        FirebaseFirestore.getInstance().collection("scheduledDriverTrips").get().addOnSuccessListener {
            if (it != null && it.documents.isNotEmpty()){
                for (d in it.documents) {
                    val driverId = d.get("driverId").toString()
                    if (dId == driverId) {
                        val timestamp: Timestamp? = d.getTimestamp("scheduledTime")
                        if (timestamp != null){
                            val rDate = timestamp.toDate()
                            val calendarToday = Calendar.getInstance()
                            val currentDate = calendarToday.time
                            val calendarDateToCheck = Calendar.getInstance()
                            calendarDateToCheck.time = rDate

                            val isToday = rDate.after(currentDate) && calendarToday.get(Calendar.YEAR) == calendarDateToCheck.get(
                                Calendar.YEAR) &&
                                    calendarToday.get(Calendar.MONTH) == calendarDateToCheck.get(
                                Calendar.MONTH) &&
                                    calendarToday.get(Calendar.DAY_OF_MONTH) == calendarDateToCheck.get(
                                Calendar.DAY_OF_MONTH)

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

    private fun initializeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    private fun initializeRepositories() {
        routesRepository = RouteRepository(RetrofitClient.googleMapsApiService)
        tripsRepository = TripsRepository()
    }

    private fun initializeViews(view: View) {
        txtPickupLocation = view.findViewById(R.id.txtPickUpLocation)
        txtAvailableSeats = view.findViewById(R.id.txtAvailableSeats)
        txtDestinationLocation = view.findViewById(R.id.txtDestinationLocation)
        btnFindDestination = view.findViewById(R.id.btnFindDestination)
        btnSchedule = view.findViewById(R.id.scheduleButton)
        btnFindRide = view.findViewById(R.id.findRideButton)
        val linearLayoutBottomSheet = view.findViewById<LinearLayout>(R.id.linearLayoutBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(linearLayoutBottomSheet)
        bottomSheetBehavior.peekHeight = 200
        bottomSheetBehavior.isHideable = false
    }

    private fun setupMapFragment() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

    }

    private fun setupListeners() {
        btnSchedule.setOnClickListener{
            startActivity(Intent(requireContext(), ScheduleTripActivity::class.java))
        }
        btnFindDestination.setOnClickListener {
            val destinationLocationString = txtDestinationLocation.text.toString()
            Log.d("RouteDebug", "Destination: $destinationLocationString")
            searchLocation(destinationLocationString)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val originLocationString = txtPickupLocation.text.toString()

                    val apiKey = getString(R.string.google_maps_routes_key)

                    val encodedPolyline = routesRepository.fetchEncodedPolyline(originLocationString, destinationLocationString, apiKey)
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
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "An error occurred while finding the route", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }



    btnFindRide.setOnClickListener { findPassengers() }
    }

    private fun findPassengers() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val originLocationString = txtPickupLocation.text.toString()
                val destinationLocationString = txtDestinationLocation.text.toString()
                val availableSeats: Int = txtAvailableSeats.text.toString().toIntOrNull() ?: 0

                if (availableSeats <= 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Please specify the number of available seats.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val originGeoPoint = routesRepository.geocodeLocation(originLocationString, requireContext())
                val destinationGeoPoint = routesRepository.geocodeLocation(destinationLocationString, requireContext())

                if (originGeoPoint != null && destinationGeoPoint != null) {

                    val driverId = FirebaseAuth.getInstance().currentUser?.uid

                    val apiKey = getString(R.string.google_maps_routes_key)
                    val encodedPolyline = routesRepository.fetchEncodedPolyline(originLocationString, destinationLocationString, apiKey)

                    if (encodedPolyline != null) {
                        val tripData = driverId?.let {
                            Trips(
                                driverId = it,
                                startLocation = originGeoPoint,
                                endLocation = destinationGeoPoint,
                                route = encodedPolyline,
                                scheduledTime = null,
                                availableSeats = availableSeats
                            )
                        }

                        if (tripData != null) {
                            SharedTripService.setTripDetails(tripData)
                        }

                        if (tripData != null) {
                            tripsRepository.storeImmediateTripsInFirestore(tripData,
                                onSuccess = { documentId ->
                                    SharedTripService.setPostedTripsDocumentId(documentId)
                                    isListeningForRequests = true

                                    startListeningForRideRequests()

                                    println("Trip successfully stored with ID: $documentId")
                                },
                                onFailure = { exception ->
                                    println("Failed to store trip data: ${exception.localizedMessage}")
                                }
                            )
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Trip successfully posted", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e("RideFinding", "Failed to calculate route: Polyline is null")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Failed to calculate route. Please try again later.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("RideFinding", "Failed to geocode locations: Origin or Destination GeoPoint is null")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Failed to geocode locations. Please check your input and try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RideFinding", "An unexpected error occurred: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "An unexpected error occurred. Please try again later.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    fun startListeningForRideRequests() {
        if (!isListeningForRequests) return

        if (driverId != null) {
            tripsRepository.listenForRideRequests(driverId, object: TripsRepository.RideRequestListener {
                override fun onNewRequest(documentIds: List<String>) {
                    if (!isListeningForRequests) return

                    SharedTripService.setRequestedTripsDocumentIds(documentIds)
                    CoroutineScope(Dispatchers.IO).launch {
                        val rideRequests = tripsRepository.fetchRideRequests(documentIds)
                        withContext(Dispatchers.Main) {
                            showOrUpdateDialog(ArrayList(rideRequests))
                        }
                    }
                }
            })
        }
    }

    override fun onRideCancellation() {
        isListeningForRequests = false
    }
    private fun showOrUpdateDialog(rideRequests: ArrayList<RideRequest>) {
        if (!isListeningForRequests) return
        val existingDialog = parentFragmentManager.findFragmentByTag("RideRequestsDialog") as? RideRequestsDialogFragment
        existingDialog?.setRideRequestDialogListener(this)
        if (existingDialog != null) {
        }
        if (existingDialog != null) {
            existingDialog.updateRideRequests(rideRequests)
        } else {
            val newDialog = RideRequestsDialogFragment.newInstance(rideRequests)
            newDialog.show(parentFragmentManager, "RideRequestsDialog")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        getLocationPermission()
        updateLocationUI()
        getDeviceLocation()
        setupMapStyle(map)

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
            // Catch exception
        }
    }
    private fun getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(requireActivity()) { task ->
                    if (task.isSuccessful && task.result != null) {
                        lastKnownLocation = task.result
                        SharedTripService.setLastKnownLocation(  lastKnownLocation!!.latitude,
                            lastKnownLocation!!.longitude)


                        // Reverse geocoding to get the address
                        val geocoder = Geocoder(requireContext(), Locale.getDefault())
                        val addresses = geocoder.getFromLocation(
                            lastKnownLocation!!.latitude,
                            lastKnownLocation!!.longitude,
                            1
                        )
                        if (addresses != null) {
                            if (addresses.isNotEmpty()) {
                                val address = addresses[0]?.getAddressLine(0)
                                txtPickupLocation.setText(address)
                            }
                        }
                        // Move the camera to the current location
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
                    .width(20f)
                    .color(Color.BLUE)
                    .geodesic(true)
                map.addPolyline(polylineOptions)
            } else {
                Toast.makeText(requireContext(), "No route found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMapStyle(map: GoogleMap) {
        map.setOnCameraIdleListener {
            val zoom = map.cameraPosition.zoom
            when {
                zoom <= 10 -> applyMapStyle(R.raw.map_style)
                zoom <= 15 -> applyMapStyle(R.raw.map_style)
                else -> applyMapStyle(R.raw.map_style)
            }
        }
    }

    private fun applyMapStyle(styleResId: Int) {
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), styleResId))
    }

    companion object {
        private const val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }

    override fun onNewRequest(documentIds: List<String>) {

    }
}
