package com.openticker.app.data.repository

import com.openticker.app.data.model.*
import com.openticker.app.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface SportsRepository { suspend fun scoreboard(league: League): List<Game> }
class EspnSportsRepository(private val http: HttpClient) : SportsRepository {
    override suspend fun scoreboard(league: League): List<Game> = withContext(Dispatchers.Default) {
        val url = "https://site.api.espn.com/apis/site/v2/sports/${league.path}/scoreboard".toHttpUrl().newBuilder()
            .addQueryParameter("dates", LocalDate.now(ZoneId.of("America/New_York")).format(DateTimeFormatter.BASIC_ISO_DATE))
            .addQueryParameter("limit", "1000")
        if (league == League.NCAAF) url.addQueryParameter("groups", "80")
        EspnParser.parse(http.get(url.build()), league)
    }
}
interface WeatherRepository {
    suspend fun forecast(location: Location): Weather
    suspend fun search(query: String): List<Location>
}
class OpenMeteoWeatherRepository(private val http: HttpClient) : WeatherRepository {
    override suspend fun forecast(location: Location): Weather = withContext(Dispatchers.Default) {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", location.latitude.toString()).addQueryParameter("longitude", location.longitude.toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code,precipitation,is_day")
            .addQueryParameter("hourly", "temperature_2m,weather_code,precipitation_probability")
            .addQueryParameter("daily", "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,uv_index_max,sunrise,sunset")
            .addQueryParameter("temperature_unit", "fahrenheit").addQueryParameter("wind_speed_unit", "mph")
            .addQueryParameter("precipitation_unit", "inch").addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "8").build()
        WeatherParser.parse(http.get(url))
    }
    override suspend fun search(query: String): List<Location> = withContext(Dispatchers.Default) {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim()).addQueryParameter("count", "8")
            .addQueryParameter("language", "en").addQueryParameter("format", "json").build()
        WeatherParser.locations(http.get(url))
    }
}
