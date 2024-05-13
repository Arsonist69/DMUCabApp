package com.example.mydmucabapp_driver.model.DataHandling


data class DirectionsResponse(
    val routes: List<Route>
)

data class Route(
    val overview_polyline: Polyline
)

data class Polyline(
    val points: String
)
