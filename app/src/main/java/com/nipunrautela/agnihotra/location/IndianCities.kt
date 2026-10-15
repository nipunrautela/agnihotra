package com.nipunrautela.agnihotra.location

import com.nipunrautela.agnihotra.R
import com.nipunrautela.agnihotra.core.Coordinates

/** Offline city-centre suggestions, not substitutes for a user's exact practice location. */
data class IndianCity(val nameResource: Int, val coordinates: Coordinates)

val indianCities = listOf(
    IndianCity(R.string.city_delhi, Coordinates(28.6139, 77.2090)),
    IndianCity(R.string.city_mumbai, Coordinates(19.0760, 72.8777)),
    IndianCity(R.string.city_chennai, Coordinates(13.0827, 80.2707)),
    IndianCity(R.string.city_kolkata, Coordinates(22.5726, 88.3639)),
    IndianCity(R.string.city_guwahati, Coordinates(26.1445, 91.7362)),
    IndianCity(R.string.city_leh, Coordinates(34.1526, 77.5771)),
)
