package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.data.remote.*
import com.openticker.app.data.repository.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Instant

class LastGoodCacheTest {
    @Test fun weatherAndEmptyScoresSurviveRestartWithOriginalTimestamp() = runTest {
        val directory = Files.createTempDirectory("last-good").toFile()
        try {
            val timestamp = Instant.parse("2026-01-10T12:00:00Z")
            val weather = WeatherParser.parse(javaClass.getResource("/weather.json")!!.readText())
            val original = WeatherSnapshot(Location.DEFAULT, weather, timestamp)
            val games = EspnParser.parse(javaClass.getResource("/espn-scoreboard.json")!!.readText(), League.NHL)
            assertTrue(LastGoodCache(directory).write(sportsCacheKey(League.NHL), ScoreSnapshot(games, timestamp)))
            assertTrue(LastGoodCache(directory).write(weatherCacheKey(Location.DEFAULT), original))
            assertTrue(LastGoodCache(directory).write(sportsCacheKey(League.NFL), ScoreSnapshot(emptyList(), timestamp)))
            val reopened = LastGoodCache(directory)
            assertEquals(games, reopened.read<ScoreSnapshot>(sportsCacheKey(League.NHL))?.games)
            assertEquals(original, reopened.read<WeatherSnapshot>(weatherCacheKey(Location.DEFAULT)))
            assertEquals(timestamp, reopened.read<ScoreSnapshot>(sportsCacheKey(League.NFL))?.updated)
            assertNull(reopened.read<WeatherSnapshot>(weatherCacheKey(Location.DEFAULT.copy(latitude = 1.0))))
        } finally { directory.deleteRecursively() }
    }
    @Test fun malformedCacheIsIgnoredAndCanBeReplaced() = runTest {
        val directory = Files.createTempDirectory("last-good").toFile()
        try {
            val cache = LastGoodCache(directory)
            val original = ScoreSnapshot(emptyList(), Instant.EPOCH)
            assertTrue(cache.write("scores", original))
            directory.listFiles()!!.single().writeText("{broken")
            assertNull(cache.read<ScoreSnapshot>("scores"))
            assertTrue(cache.write("scores", original))
            assertEquals(original, cache.read<ScoreSnapshot>("scores"))
        } finally { directory.deleteRecursively() }
    }
    @Test fun unwritableCacheDoesNotCrashAndKeysDoNotBecomePaths() = runTest {
        val directory = Files.createTempDirectory("last-good").toFile()
        try {
            val blocked = File(directory, "file").apply { writeText("occupied") }
            assertFalse(LastGoodCache(blocked).write("scores", ScoreSnapshot(emptyList(), Instant.EPOCH)))
            assertTrue(LastGoodCache(directory).write("../../secret?token=x", ScoreSnapshot(emptyList(), Instant.EPOCH)))
            assertTrue(directory.listFiles()!!.filter { it.extension == "json" }.all { it.name.matches(Regex("v1-[a-f0-9]{64}\\.json")) })
        } finally { directory.deleteRecursively() }
    }
    @Test fun providerErrorsAreNotPresentedAsDeviceOffline() {
        assertTrue(feedFailureMessage(FeedHttpException(503), "Weather").contains("service error (HTTP 503)"))
        assertTrue(feedFailureMessage(IOException(), "NFL").contains("connection failed"))
        assertTrue(feedFailureMessage(IllegalArgumentException(), "Weather").contains("response could not be read"))
    }
}
