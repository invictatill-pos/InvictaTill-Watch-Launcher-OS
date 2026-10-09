package com.healthsync.phone.data

import com.healthsync.phone.data.model.WorkoutEntity
import com.healthsync.phone.data.model.LatLngPoint
import com.healthsync.phone.ui.workout.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

class WorkoutExportTest {
    private fun workout(activeSeconds: Int = 300) = WorkoutEntity(
        id = 1, activityType = "Walk, \"Morning\"", startTime = 1000, endTime = 601_000,
        distanceMeters = 1000f, calories = 40.0, avgHeartRate = 75, steps = 1500,
        routeJson = "[]", durationSeconds = activeSeconds
    )

    @Test fun exportAndSummaryUseActiveTimeInsteadOfPausedElapsedTime() {
        assertEquals(300L, workoutDurationSeconds(workout()))
        assertTrue(workoutSummary(workout()).contains("Duration: 5m 0s"))
        assertTrue(workoutsCsv(listOf(workout())).contains(",300,1000.0,40.0,75,1500,0"))
    }

    @Test fun legacyDurationAndQuotedCsvFieldsArePreserved() {
        assertEquals(600L, workoutDurationSeconds(workout(0)))
        assertTrue(workoutsCsv(listOf(workout())).contains("\"Walk, \"\"Morning\"\"\""))
    }

    @Test fun exportedGpxIsReadableXmlWithCoordinatesAndUtcTime() {
        val gpx = workoutGpx(workout(), listOf(LatLngPoint(22.5, 88.4, 1000)))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(gpx.toByteArray(Charsets.UTF_8)))
        val point = document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt").item(0)
        assertEquals("22.5", point.attributes.getNamedItem("lat").nodeValue)
        assertEquals("88.4", point.attributes.getNamedItem("lon").nodeValue)
        assertTrue(point.textContent.contains("1970-01-01T00:00:01Z"))
    }

    @Test fun pausedAndReacquiredRoutesExportAsSeparateTrackSegments() {
        val route = listOf(LatLngPoint(22.5, 88.4, 1000), LatLngPoint(22.5001, 88.4001, 2000),
            LatLngPoint(22.6, 88.5, 4000, segmentStart = true), LatLngPoint(22.6001, 88.5001, 5000))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(workoutGpx(workout(), route).toByteArray(Charsets.UTF_8)))
        val segments = document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkseg")
        assertEquals(2, segments.length)
        assertEquals(2, segments.item(0).childNodes.let { children -> (0 until children.length).count { children.item(it).localName == "trkpt" } })
        assertEquals(4, document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt").length)
    }
}
