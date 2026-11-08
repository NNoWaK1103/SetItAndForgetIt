package com.openticker.app.data.remote

import com.openticker.app.data.model.*
import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.LocalDateTime

internal fun JsonObject.obj(key: String) = this[key] as? JsonObject
internal fun JsonObject.arr(key: String) = (this[key] as? JsonArray)?.toList().orEmpty()
internal fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.num(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.booleanOrNull
private fun <T> parsed(value: String?, parse: (String) -> T): T? = value?.let { runCatching { parse(it) }.getOrNull() }

object EspnParser {
    fun status(name: String?, state: String?, completed: Boolean?): GameStatus = when {
        name?.contains("POSTPON", true) == true || name?.contains("DELAY", true) == true || name?.contains("SUSPEND", true) == true -> GameStatus.POSTPONED
        name?.contains("CANCEL", true) == true -> GameStatus.CANCELED
        completed == true || state == "post" -> GameStatus.FINAL
        state == "in" -> GameStatus.LIVE
        state == "pre" -> GameStatus.UPCOMING
        else -> GameStatus.UNKNOWN
    }
    fun parse(body: String, league: League): List<Game> {
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["events"] is JsonArray) { "Scoreboard response has no events array" }
        return root.arr("events").mapNotNull { item ->
            val e = item as? JsonObject ?: return@mapNotNull null
            val c = e.arr("competitions").firstOrNull() as? JsonObject ?: return@mapNotNull null
            val teams = c.arr("competitors").filterIsInstance<JsonObject>()
            val away = teams.find { it.str("homeAway") == "away" } ?: return@mapNotNull null
            val home = teams.find { it.str("homeAway") == "home" } ?: return@mapNotNull null
            val id = e.str("id") ?: return@mapNotNull null
            val s = c.obj("status") ?: e.obj("status") ?: JsonObject(emptyMap())
            val type = s.obj("type") ?: JsonObject(emptyMap())
            val mapped = status(type.str("name"), type.str("state"), type.bool("completed"))
            Game(id, league, team(away), team(home), away.int("score"), home.int("score"), mapped,
                parsed(c.str("date") ?: e.str("date")) { OffsetDateTime.parse(it).toInstant() }, s.int("period"), s.str("displayClock"),
                type.str("shortDetail") ?: type.str("detail") ?: type.str("description") ?: "Status unavailable",
                venue = c.obj("venue")?.text("fullName"),
                broadcasts = broadcasts(c),
                situation = if (mapped == GameStatus.LIVE) situation(c.obj("situation"), league) else null)
        }.distinctBy { it.id }
    }
    // Optional provider metadata must never prevent the scoreboard from loading.
    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun team(c: JsonObject): Team {
        val t = c.obj("team") ?: JsonObject(emptyMap())
        val records = c.arr("records").filterIsInstance<JsonObject>()
        val overall = records.firstOrNull { it.str("type") == "total" || it.str("name") == "overall" }
        return Team(t.str("displayName") ?: t.str("name") ?: "Unknown team", t.str("abbreviation") ?: "—",
            t.str("logo") ?: (t.arr("logos").firstOrNull() as? JsonObject)?.str("href"),
            id = t.str("id") ?: c.str("id"), record = overall?.text("summary"),
            // ESPN uses 99 for an unranked team.
            rank = c.obj("curatedRank")?.int("current")?.takeIf { it in 1..25 })
    }

    private fun broadcasts(c: JsonObject): List<String> {
        val names = c.arr("broadcasts").filterIsInstance<JsonObject>().flatMap { it.arr("names") }
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.takeIf(String::isNotEmpty) }
        val geo = c.arr("geoBroadcasts").filterIsInstance<JsonObject>()
            .mapNotNull { it.obj("media")?.text("shortName") }
        return (names + geo).distinct()
    }

    private fun situation(s: JsonObject?, league: League): GameSituation? {
        if (s == null) return null
        val football = league == League.NFL || league == League.NCAAF
        val baseball = league == League.MLB
        return GameSituation(
            possessionTeamId = if (football) s.str("possession") else null,
            downDistance = if (football) s.text("downDistanceText") ?: s.text("shortDownDistanceText") else null,
            balls = if (baseball) s.int("balls")?.takeIf { it in 0..3 } else null,
            strikes = if (baseball) s.int("strikes")?.takeIf { it in 0..2 } else null,
            outs = if (baseball) s.int("outs")?.takeIf { it in 0..3 } else null,
            onFirst = if (baseball) s.bool("onFirst") else null,
            onSecond = if (baseball) s.bool("onSecond") else null,
            onThird = if (baseball) s.bool("onThird") else null,
            lastPlay = s.obj("lastPlay")?.text("text")
        ).takeUnless { it == GameSituation() }
    }

}

object WeatherParser {
    fun parse(body: String): Weather {
        val r = Json.parseToJsonElement(body).jsonObject
        val c = requireNotNull(r.obj("current")) { "Current weather is missing" }
        val h = r.obj("hourly") ?: JsonObject(emptyMap())
        val d = r.obj("daily") ?: JsonObject(emptyMap())
        fun number(o: JsonObject, key: String, index: Int) = (o.arr(key).getOrNull(index) as? JsonPrimitive)?.doubleOrNull
        fun string(o: JsonObject, key: String, index: Int) = (o.arr(key).getOrNull(index) as? JsonPrimitive)?.contentOrNull
        val hours = h.arr("time").indices.mapNotNull { i ->
            val time = parsed(string(h, "time", i), LocalDateTime::parse) ?: return@mapNotNull null
            HourWeather(time, number(h, "temperature_2m", i), number(h, "weather_code", i)?.toInt(), number(h, "precipitation_probability", i)?.toInt())
        }
        val days = d.arr("time").indices.mapNotNull { i ->
            val date = parsed(string(d, "time", i), LocalDate::parse) ?: return@mapNotNull null
            DayWeather(date, number(d, "weather_code", i)?.toInt(), number(d, "temperature_2m_max", i),
                number(d, "temperature_2m_min", i), number(d, "precipitation_probability_max", i)?.toInt(),
                number(d, "uv_index_max", i), parsed(string(d, "sunrise", i), LocalDateTime::parse), parsed(string(d, "sunset", i), LocalDateTime::parse))
        }
        return Weather(requireNotNull(c.num("temperature_2m")) { "Temperature is missing" }, c.num("apparent_temperature"),
            c.int("relative_humidity_2m"), c.num("wind_speed_10m"), c.num("wind_direction_10m"), c.int("weather_code"),
            c.num("precipitation"), c.int("is_day") != 0, r.str("timezone") ?: "UTC", hours, days)
    }
    fun locations(body: String): List<Location> = Json.parseToJsonElement(body).jsonObject.arr("results").mapNotNull {
        val o = it as? JsonObject ?: return@mapNotNull null
        Location(o.str("name") ?: return@mapNotNull null, listOfNotNull(o.str("admin1"), o.str("country_code")).joinToString(", "),
            o.num("latitude") ?: return@mapNotNull null, o.num("longitude") ?: return@mapNotNull null)
    }
}
