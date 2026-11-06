@file:kotlinx.serialization.UseSerializers(InstantSerializer::class)

package com.openticker.app.data.repository

import com.openticker.app.data.model.*
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class WeatherSnapshot(val location: Location, val weather: Weather, val updated: Instant)
@Serializable
data class ScoreSnapshot(val games: List<Game>, val updated: Instant)
fun weatherCacheKey(location: Location) = "weather:${location.latitude}:${location.longitude}"
fun sportsCacheKey(league: League) = "sports:${league.name}"
