package com.example.mydmucabapp_passenger.helpers

import com.example.mydmucabapp_driver.api.GoogleMapsApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import java.io.IOException
import java.util.Locale

class RouteCreation(private val googleMapsApiService: GoogleMapsApiService) {



    suspend fun fetchEncodedPolyline(origin: String, destination: String, apiKey: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = googleMapsApiService.getDirections(origin, destination, apiKey).execute()
                if (response.isSuccessful) {
                    response.body()?.routes?.firstOrNull()?.overview_polyline?.points
                } else {
                    Log.e("MatchHandler", "Failed to fetch encoded polyline: ${response.errorBody()?.string() ?: "Unknown error"}")
                    null
                }
            } catch (e: Exception) {
                Log.e("MatchHandler", "Exception fetching encoded polyline", e)
                null
            }
        }
    }


    fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }






    suspend fun geocodeLocation(locationName: String, context: Context): GeoPoint? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val addresses: MutableList<Address>? = geocoder.getFromLocationName(locationName, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                GeoPoint(address.latitude, address.longitude)
            } else {
                null
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
