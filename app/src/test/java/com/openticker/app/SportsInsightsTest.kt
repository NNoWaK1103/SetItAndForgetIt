package com.openticker.app

import com.openticker.app.data.model.*
import com.openticker.app.util.sportsGameBrief
import com.openticker.app.util.sportsStatusCaption
import org.junit.Assert.*
import org.junit.Test

class SportsInsightsTest {
    private fun game(
        league: League = League.NFL,
        status: GameStatus = GameStatus.LIVE,
        situation: GameSituation? = null,
        venue: String? = null,
        broadcasts: List<String> = emptyList()
    ) = Game("brief", league, Team("Away", "AWY", id="1"), Team("Home", "HME", id="2"),
        14, 10, status, null, null, null, "", venue, broadcasts, situation)

    @Test fun `football prioritizes situation and actual last play without a tap`() {
        val brief = sportsGameBrief(game(situation=GameSituation(
            possessionTeamId="1", downDistance="3rd & 4 at HME 30", lastPlay="Pass complete."),
            broadcasts=listOf("ABC"), venue="Field"))
        assertEquals(listOf("3rd & 4 at HME 30 • AWY ball", "Last play: Pass complete."), brief)
    }

    @Test fun `baseball preserves zero count outs and occupied bases`() {
        val brief = sportsGameBrief(game(league=League.MLB, situation=GameSituation(
            balls=0, strikes=2, outs=0, onFirst=true, onSecond=false, onThird=true), broadcasts=listOf("ESPN")))
        assertEquals(listOf("0-2 count • 0 outs • On 1st, 3rd", "Coverage: ESPN"), brief)
    }

    @Test fun `nonlive cards use coverage and venue not leftover play or possession`() {
        GameStatus.entries.filter { it != GameStatus.LIVE }.forEach { status ->
            assertEquals(listOf("Coverage: ABC / ESPN", "Venue: Test Field"), sportsGameBrief(game(
                status=status, situation=GameSituation(possessionTeamId="1", downDistance="1st & 10", lastPlay="Old play"),
                broadcasts=listOf("ABC", "ESPN"), venue="Test Field")))
        }
    }

    @Test fun `missing and blank fields do not invent context`() {
        assertTrue(sportsGameBrief(game()).isEmpty())
        assertTrue(sportsGameBrief(game(situation=GameSituation(lastPlay=" \n "), venue="  ", broadcasts=listOf("\t"))).isEmpty())
        assertEquals(listOf("Venue: Field"), sportsGameBrief(game(venue="Field")))
    }

    @Test fun `long provider text stays in two rows and whitespace is normalized`() {
        val longPlay = "A long provider play description. ".repeat(30).trim()
        val brief = sportsGameBrief(game(situation=GameSituation(lastPlay="\n$longPlay\n"),
            broadcasts=listOf(" ABC ", "ABC", "\n ESPN\t"), venue="Field"))
        assertEquals(2, brief.size)
        assertEquals("Last play: $longPlay", brief[0])
        assertEquals("Coverage: ABC / ESPN", brief[1])
        assertTrue(brief.none { '\n' in it || '\t' in it })
        // The UI ellipsizes each row rather than expanding the card or altering provider words.
    }

    @Test fun `saved live status is never labeled current live`() {
        assertEquals("● LIVE", sportsStatusCaption(game()))
        assertEquals("SAVED • WAS LIVE", sportsStatusCaption(game(), stale=true))
        assertEquals("SAVED • FINAL", sportsStatusCaption(game(status=GameStatus.FINAL), stale=true))
        assertEquals("UPCOMING", sportsStatusCaption(game(status=GameStatus.UPCOMING)))
    }
}
