package com.healthsync.phone.service

import org.junit.Assert.*
import org.junit.Test

class NavigationNotificationParserTest {
    @Test fun nextTurnDistanceAndInstructionRemainSeparateFromTripSummary() {
        val result = parse(title = "200 m – Turn right", text = "MG Road", subText = "15 min · 8 km · 4:20 PM ETA")!!
        assertEquals("200 m", result.distance)
        assertEquals("Turn right", result.instruction)
        assertEquals("MG Road", result.road)
        assertEquals("15 min · 8 km · 4:20 PM ETA", result.summary)
    }

    @Test fun distanceOnlyTitleUsesRealBodyInstruction() {
        val result = parse(title = "0.5 mi", text = "Keep left toward Pine Avenue")!!
        assertEquals("0.5 mi", result.distance)
        assertEquals("Keep left toward Pine Avenue", result.instruction)
        assertEquals("", result.road)
    }

    @Test fun titleInstructionUsesBodyRoadWithoutGuessingManeuver() {
        val result = parse(title = "At the roundabout, take the third exit", text = "NH 48")!!
        assertEquals("At the roundabout, take the third exit", result.instruction)
        assertEquals("", result.distance)
        assertEquals("NH 48", result.road)
    }

    @Test fun distanceOnlyBodyIsNotCalledARoad() {
        val result = parse(title = "Turn left", text = "In 100 ft")!!
        assertEquals("100 ft", result.distance)
        assertEquals("", result.road)
    }

    @Test fun tripSummaryNeverBecomesNextTurnDistance() {
        val result = parse(title = "Continue on the current road", text = "Main Street", subText = "12 min · 3 km")!!
        assertEquals("", result.distance)
        assertEquals("12 min · 3 km", result.summary)
    }

    @Test fun unicodeSourceTextIsPreservedWithoutEnglishManeuverInference() {
        val result = parse(title = "दाएँ मुड़ें", text = "महात्मा गांधी रोड", subText = "१२ मिनट · ५ किमी")!!
        assertEquals("दाएँ मुड़ें", result.instruction)
        assertEquals("महात्मा गांधी रोड", result.road)
        assertEquals("१२ मिनट · ५ किमी", result.summary)
        assertEquals("", result.distance)
        val arabic = parse(title = "انعطف يمينًا", text = "شارع الملك")!!
        assertEquals("انعطف يمينًا", arabic.instruction)
    }

    @Test fun unknownDistanceUnitsStayInOriginalText() {
        val result = parse(title = "२०० मीटर पर दाएँ मुड़ें", text = "आगे")!!
        assertEquals("२०० मीटर पर दाएँ मुड़ें", result.instruction)
        assertEquals("", result.distance)
    }

    @Test fun localizedDigitsAndDecimalDistancesAreSourceValues() {
        assertEquals("१,५ km", parse(title = "१,५ km – बाएँ मुड़ें")!!.distance)
        assertEquals("250 meters", parse(title = "250 meters — Keep left")!!.distance)
    }

    @Test fun minutesNeverQualifyAsMeters() {
        assertEquals("", parse(title = "2 min", text = "Continue straight")!!.distance)
        assertEquals("", parse(title = "12 minutes", text = "Turn left")!!.distance)
        assertEquals("2 m", parse(title = "2 m – Turn right")!!.distance)
        assertEquals("2 mi", parse(title = "2 mi – Turn right")!!.distance)
    }

    @Test fun blankTitlesUseAvailableSourceText() {
        assertEquals("Turn right", parse(title = "", text = "Turn right")!!.instruction)
        assertEquals("Continue straight", parse(title = "", text = "", bigText = "Continue straight")!!.instruction)
        assertNull(parse(title = "", text = "", bigText = "", subText = "12 min"))
    }

    @Test fun duplicateSummaryTextIsNotRepeated() {
        val result = parse(title = "Turn right", text = "MG Road", bigText = "12 min · 3 km", subText = "12 min · 3 km")!!
        assertEquals("12 min · 3 km", result.summary)
        assertEquals("", parse(title = "Turn right", text = "MG Road", bigText = "MG Road")!!.summary)
    }

    @Test fun foreignAppsNonOngoingAndGroupSummariesAreRejected() {
        assertNull(NavigationNotificationParser.parse(source().copy(packageName = "com.example.fake.maps")))
        assertNull(NavigationNotificationParser.parse(source().copy(ongoing = false)))
        assertNull(NavigationNotificationParser.parse(source().copy(groupSummary = true)))
        assertNull(NavigationNotificationParser.parse(source().copy(key = "")))
    }

    @Test fun downloadsAndTrafficDoNotQualifyAsActiveNavigation() {
        assertNull(NavigationNotificationParser.parse(source().copy(title = "Downloading offline maps", progress = true)))
        assertNull(parse(title = "Offline maps downloaded"))
        assertNull(parse(title = "Traffic update", text = "Delays on your commute"))
        assertNull(NavigationNotificationParser.parse(source().copy(category = null, title = "Maps is running", text = "Tap to open")))
    }

    @Test fun legacyFallbackRequiresRealNavigationEvidence() {
        assertNotNull(NavigationNotificationParser.parse(source().copy(category = null, title = "200 m – Turn right")))
        assertNotNull(NavigationNotificationParser.parse(source().copy(category = null, title = "Continue straight", actionTitles = listOf("Exit navigation"))))
        assertNull(NavigationNotificationParser.parse(source().copy(category = null, title = "200 m", text = "Nearby restaurant")))
        assertNull(NavigationNotificationParser.parse(source().copy(category = null, title = "Turn right", text = "Suggested route")))
    }

    @Test fun fieldsAreBoundedAndSanitizedWithoutSplittingEmoji() {
        val result = parse(title = "a".repeat(299) + "😀", text = "r".repeat(900), subText = "s".repeat(900))!!
        assertEquals(299, result.instruction.length)
        assertEquals(300, result.road.length)
        assertEquals(300, result.summary.length)
        assertEquals("Turn right", parse(title = "\u0000Turn right\u0000")!!.instruction)
        val key = "k".repeat(2_000)
        assertTrue(NavigationNotificationParser.parse(source().copy(key = key))!!.sessionId.length <= 512)
    }

    @Test fun sourceNotificationKeyIdentifiesTheRouteSession() {
        assertEquals("route-123", NavigationNotificationParser.parse(source().copy(key = "route-123"))!!.sessionId)
    }

    @Test fun EnglishManeuversComeOnlyFromAnchoredInstructionText() {
        assertEquals("RIGHT", parse(title = "200 m – Turn right", text = "Left Lane Road")!!.maneuver)
        assertEquals("LEFT", parse(title = "Turn left onto Right Street")!!.maneuver)
        assertEquals("FORK_LEFT", parse(title = "Keep left at the fork")!!.maneuver)
        assertEquals("FORK_RIGHT", parse(title = "Keep right")!!.maneuver)
        assertEquals("STRAIGHT", parse(title = "Continue straight for 2 km")!!.maneuver)
        assertEquals("SHARP_RIGHT", parse(title = "Turn sharply right")!!.maneuver)
        assertEquals("SLIGHT_LEFT", parse(title = "Slight left onto the ramp")!!.maneuver)
        assertEquals("UTURN_LEFT", parse(title = "Make a U-turn to the left")!!.maneuver)
        assertEquals("UTURN_RIGHT", parse(title = "Make a U-turn right")!!.maneuver)
        assertEquals("ROUNDABOUT", parse(title = "At the roundabout, take the 3rd exit")!!.maneuver)
        assertEquals("MERGE", parse(title = "Merge onto NH 48")!!.maneuver)
        assertEquals("ARRIVE", parse(title = "You have arrived at your destination")!!.maneuver)
    }

    @Test fun UnknownOrLocalizedInstructionsNeverInventArrows() {
        assertEquals("UNKNOWN", parse(title = "Left Lane Road", text = "Turn right")!!.maneuver)
        assertEquals("UNKNOWN", parse(title = "Road to the right")!!.maneuver)
        assertEquals("UNKNOWN", parse(title = "Make a U-turn")!!.maneuver)
        assertEquals("UNKNOWN", parse(title = "दाएँ मुड़ें")!!.maneuver)
        assertEquals("UNKNOWN", parse(title = "After the bridge, turn left")!!.maneuver)
    }

    @Test fun legacyOrDisconnectedWatchesKeepOrdinaryMapsNotifications() {
        assertTrue(NavigationNotificationParser.shouldReplaceOrdinary(true, true, true))
        assertFalse(NavigationNotificationParser.shouldReplaceOrdinary(false, true, true))
        assertFalse(NavigationNotificationParser.shouldReplaceOrdinary(true, false, true))
        assertFalse(NavigationNotificationParser.shouldReplaceOrdinary(true, true, false))
    }

    private fun parse(title: String = "Turn right", text: String = "", bigText: String = "", subText: String = "") =
        NavigationNotificationParser.parse(source().copy(title = title, text = text, bigText = bigText, subText = subText))

    private fun source() = NavigationNotificationParser.Source(
        packageName = NavigationNotificationParser.MAPS_PACKAGE, key = "maps-navigation", ongoing = true,
        category = "navigation", title = "Turn right")
}
