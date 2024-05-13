package com.example.mydmucabapp_passenger.routeMatching


import android.util.Log
import com.example.mydmucabapp_passenger.model.DataHandling.TripsRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt



class MatchHandler(private val tripsRepository: TripsRepository) {







    suspend fun matchDriverWithPassenger(origin: GeoPoint, destination: GeoPoint, collectionName: String = "immediatePostedTrips", completion: (Boolean, List<String>?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val proximityMatchCompletion = CompletableDeferred<Pair<Boolean, List<String>?>>()

            findNearbyDrivers(origin, destination, collectionName) { success, driversByProximity ->
                if (success) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val driversWithSeats = driversByProximity.filter { documentId ->
                            tripsRepository.checkDriverAvailableSeats(documentId, collectionName) > 0
                        }
                        proximityMatchCompletion.complete(Pair(true, driversWithSeats))
                    }
                } else {
                    proximityMatchCompletion.complete(Pair(false, null))
                }
            }

            val (proximitySuccess, filteredDrivers) = proximityMatchCompletion.await()

            withContext(Dispatchers.Main) {
                if (proximitySuccess && filteredDrivers != null && filteredDrivers.isNotEmpty()) {
                    completion(true, filteredDrivers)
                } else {
                    val routeMatchingDrivers = findMatchingDriverRoute(origin, destination, collectionName)
                    if (routeMatchingDrivers.isNotEmpty()) {
                        completion(true, routeMatchingDrivers)
                    } else {
                        completion(false, null)
                    }
                }
            }
        }
    }



    fun findNearbyDrivers(origin: GeoPoint, destination: GeoPoint, collectionName: String, completion: (Boolean, List<String>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val documents = tripsRepository.fetchImmediatePostedTripsFromDatabase(collectionName)
            val driversNearOrigin = mutableSetOf<String>()
            val driversNearDestination = mutableSetOf<String>()

            documents.forEach { document ->
                val tripStartLocation = document["startLocation"] as? GeoPoint
                val tripEndLocation = document["endLocation"] as? GeoPoint
                val tripId = document.id

                tripStartLocation?.let {
                    if (calculateDistance(origin.latitude, origin.longitude, it.latitude, it.longitude) <= 10.0) {
                        driversNearOrigin.add(tripId)
                    }
                }

                tripEndLocation?.let {
                    if (calculateDistance(destination.latitude, destination.longitude, it.latitude, it.longitude) <= 10.0) {
                        driversNearDestination.add(tripId)
                    }
                }
            }

            val matchedDrivers = driversNearOrigin.intersect(driversNearDestination).toList()
            withContext(Dispatchers.Main) {
                completion(matchedDrivers.isNotEmpty(), matchedDrivers)
            }
        }
    }

    suspend fun findMatchingDriverRoute(origin: GeoPoint, destination: GeoPoint, collectionName: String): List<String> {
        Log.d("RouteMatching", "Starting to find matching driver routes.")
        val immediateTrips = tripsRepository.fetchImmediatePostedTripsFromDatabase(collectionName)
        val matchingDrivers = mutableListOf<String>()

        Log.d("RouteMatching", "Fetched ${immediateTrips.size} immediate trips from database.")

        immediateTrips.forEachIndexed { index, trip ->
            val availableSeats = trip["availableSeats"] as? Long ?
            Log.d("RouteMatching", "Trip $index: Available seats = $availableSeats")

            if (availableSeats != null) {
                if (availableSeats > 0) {
                    val driverRouteEncoded = trip["route"] as? String ?: return@forEachIndexed
                    Log.d("RouteMatching", "Trip $index: Encoded route found.")

                    val driverRoute = decodePolyline(driverRouteEncoded)
                    Log.d("RouteMatching", "Trip $index: Decoded route into ${driverRoute.size} points.")

                    val tripId = trip.id
                    Log.d("RouteMatching", "Trip $index: ID = $tripId")

                    val isOriginNearRoute = driverRoute.any { driverPoint ->
                        val distance = calculateDistance(origin.latitude, origin.longitude, driverPoint.latitude, driverPoint.longitude)
                        Log.d("RouteMatching", "Checking distance from origin to point: $distance km")
                        distance <= 10.0
                    }

                    val isDestinationNearRoute = driverRoute.any { driverPoint ->
                        val distance = calculateDistance(destination.latitude, destination.longitude, driverPoint.latitude, driverPoint.longitude)
                        Log.d("RouteMatching", "Checking distance from destination to point: $distance km")
                        distance <= 10.0
                    }

                    if (isOriginNearRoute && isDestinationNearRoute) {
                        matchingDrivers.add(tripId)
                        Log.d("RouteMatching", "Trip $index: Matches both origin and destination criteria.")
                    } else {
                        Log.d("RouteMatching", "Trip $index: Does not match both criteria.")
                    }
                } else {
                    Log.d("RouteMatching", "Trip $index: No available seats.")
                }
            }
        }

        Log.d("RouteMatching", "Found ${matchingDrivers.size} matching drivers.")
        return matchingDrivers
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371 // Radius of the earth in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = ArrayList<GeoPoint>()
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

            val p = GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }














}