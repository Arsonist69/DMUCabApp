package com.example.mydmucabapp_passenger.helpers
import android.content.Context
import android.location.Geocoder
import com.google.firebase.firestore.GeoPoint
import java.io.IOException
import java.util.*
class GeoPointDecoder {
    companion object{

        fun getGeoLocationName(context: Context, geoPoint: GeoPoint?): String {
            if (geoPoint != null){
                val geocoder = Geocoder(context, Locale.getDefault())
                val latitude = geoPoint.latitude
                val longitude = geoPoint.longitude

                try {
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val locationName = addresses[0].getAddressLine(0)
                        return locationName
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            return geoPoint.toString()
        }
    }
}