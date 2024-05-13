import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MapHandler(private val context: Context, private val googleMap: GoogleMap, private val fusedLocationClient: FusedLocationProviderClient) {

    private var locationPermissionGranted = false
    private val defaultLocation = LatLng(-33.8523341, 151.2106085)

    fun getLocationPermission(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == MY_PERMISSIONS_REQUEST_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true
                updateLocationUI()
            }
        }
    }

    fun updateLocationUI() {
        try {
            if (locationPermissionGranted) {
                googleMap.isMyLocationEnabled = true
                googleMap.uiSettings.isMyLocationButtonEnabled = true
            } else {
                googleMap.isMyLocationEnabled = false
                googleMap.uiSettings.isMyLocationButtonEnabled = false
                requestLocationPermission()
            }
        } catch (e: SecurityException) {
            e.message?.let { Log.e("Exception: %s", it) }
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), MY_PERMISSIONS_REQUEST_LOCATION)
        } else {
            locationPermissionGranted = true
        }
    }

    fun getDeviceLocation(callback: (LatLng?) -> Unit) {
        try {
            if (locationPermissionGranted) {
                val locationResult = fusedLocationClient.lastLocation
                locationResult.addOnCompleteListener(context as Activity) { task ->
                    if (task.isSuccessful && task.result != null) {
                        val lastKnownLocation = task.result
                        callback(LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude))
                    } else {
                        callback(null)
                    }
                }
            } else {
                callback(null)
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    fun searchLocation(locationName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val geocoder = Geocoder(context)
            try {
                val addressList = geocoder.getFromLocationName(locationName, 1)
                if (addressList != null) {
                    if (addressList.isNotEmpty()) {
                        val address = addressList[0]
                        val latLng = LatLng(address.latitude, address.longitude)
                        withContext(Dispatchers.Main) {
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                            googleMap.addMarker(MarkerOptions().position(latLng).title(locationName))
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error finding location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun drawRouteOnMap(polylinePoints: List<LatLng>) {
        CoroutineScope(Dispatchers.Main).launch {
            if (polylinePoints.isNotEmpty()) {
                val polylineOptions = PolylineOptions().addAll(polylinePoints).width(10f).color(
                    Color.BLUE).geodesic(true)
                googleMap.addPolyline(polylineOptions)
            } else {
                Toast.makeText(context, "No route found", Toast.LENGTH_SHORT).show()
            }
        }
    }



    companion object {
        const val MY_PERMISSIONS_REQUEST_LOCATION = 99
    }
}
