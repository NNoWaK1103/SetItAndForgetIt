package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.data.remote.EspnParser
import com.openticker.app.ui.components.liveSituationText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class SportsContextTest {
    private fun scoreboard(extra: String = "", state: String = "in") = """
        {"events":[{"id":"context","competitions":[{
          "competitors":[
            {"homeAway":"away","score":"14","team":{"id":"1","displayName":"Away","abbreviation":"AWY"},
             "records":[{"type":"home","summary":"3-0"},{"name":"overall","type":"total","summary":"6-2"}],"curatedRank":{"current":7}},
            {"homeAway":"home","score":"10","team":{"id":"2","displayName":"Home","abbreviation":"HME"},"curatedRank":{"current":99}}
          ],"status":{"type":{"state":"$state","shortDetail":"Provider status"}}$extra
        }]}]}
    """.trimIndent()

    @Test fun `provider records ranks venue and broadcast are optional across leagues`() {
        League.entries.forEach { league ->
            val game = EspnParser.parse(scoreboard(""",
                "venue":{"fullName":" Test Field "},
                "broadcasts":[{"names":["ABC","ESPN",null,{}]}],
                "geoBroadcasts":[{"media":{"shortName":"ABC"}}]
            """), league).single()
            assertEquals("6-2", game.away.record)
            assertEquals(7, game.away.rank)
            assertNull(game.home.rank)
            assertNull(game.home.record)
            assertEquals("Test Field", game.venue)
            assertEquals(listOf("ABC", "ESPN"), game.broadcasts)
        }
    }

    @Test fun `football situation maps possession and down distance without inventing details`() {
        listOf(League.NFL, League.NCAAF).forEach { league ->
            val game = EspnParser.parse(scoreboard(""",
                "situation":{"possession":"1","downDistanceText":"3rd & 4 at HME 30","lastPlay":{"text":"Pass complete."}}
            """), league).single()
            assertEquals("1", game.situation?.possessionTeamId)
            assertEquals("3rd & 4 at HME 30 • AWY ball", liveSituationText(game))
            assertEquals("Pass complete.", game.situation?.lastPlay)
            assertNull(game.situation?.balls)
        }
    }

    @Test fun `baseball situation preserves zero count and occupied bases`() {
        val game = EspnParser.parse(scoreboard(""",
            "situation":{"balls":0,"strikes":2,"outs":1,"onFirst":true,"onSecond":false,"onThird":true,"possession":"1"}
        """), League.MLB).single()
        assertEquals("0-2 count • 1 out • On 1st, 3rd", liveSituationText(game))
        assertEquals(false, game.situation?.onSecond)
        assertNull(game.situation?.possessionTeamId)
    }

    @Test fun `malformed optional context cannot break valid game`() {
        val game = EspnParser.parse(scoreboard(""",
            "venue":[],"broadcasts":[null,{},true],"geoBroadcasts":{},
            "situation":{"balls":9,"strikes":-1,"outs":"bad","onFirst":"yes","lastPlay":{"text":{}}}
        """), League.MLB).single()
        assertNull(game.venue)
        assertTrue(game.broadcasts.isEmpty())
        assertNull(game.situation)
        assertEquals("", liveSituationText(game))
    }

    @Test fun `non live games never show stale possession and play context`() {
        listOf("pre", "post").forEach { state ->
            val game = EspnParser.parse(scoreboard(""", "situation":{"possession":"1","downDistanceText":"1st & 10"}""", state), League.NFL).single()
            assertNull(game.situation)
            assertEquals("", liveSituationText(game))
        }
    }

    @Test fun `basketball and hockey ignore other sports situation fields`() {
        listOf(League.NBA, League.NHL).forEach { league ->
            val game = EspnParser.parse(scoreboard(""", "situation":{"possession":"1","balls":1,"outs":2,"downDistanceText":"1st & 10"}"""), league).single()
            assertNull(game.situation)
        }
    }

    @Test fun `missing overall record does not mislabel home record`() {
        val body = scoreboard().replace("\"name\":\"overall\",\"type\":\"total\"", "\"name\":\"road\",\"type\":\"road\"")
        assertNull(EspnParser.parse(body, League.NFL).single().away.record)
    }

    @Test fun `legacy saved game loads with defaults and enriched game round trips`() {
        val legacy = """{"id":"legacy","league":"NFL","away":{"name":"Away","abbreviation":"A"},"home":{"name":"Home","abbreviation":"H"},"awayScore":null,"homeScore":null,"status":"UPCOMING","start":null,"period":null,"clock":null,"detail":"Scheduled"}"""
        val oldGame = Json.decodeFromString<Game>(legacy)
        assertNull(oldGame.away.record)
        assertNull(oldGame.away.rank)
        assertNull(oldGame.away.id)
        assertNull(oldGame.venue)
        assertNull(oldGame.situation)
        assertTrue(oldGame.broadcasts.isEmpty())
        val game = EspnParser.parse(scoreboard(""", "situation":{"possession":"1","downDistanceText":"1st & 10"},"venue":{"fullName":"Field"},"broadcasts":[{"names":["ABC"]}]"""), League.NFL).single()
        assertEquals(game, Json.decodeFromString<Game>(Json.encodeToString(game)))
    }
}
