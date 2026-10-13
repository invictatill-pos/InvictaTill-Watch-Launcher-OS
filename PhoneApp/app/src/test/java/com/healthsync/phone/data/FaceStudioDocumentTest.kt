package com.healthsync.phone.data

import com.healthsync.phone.data.watchface.FaceLayer
import com.healthsync.phone.data.watchface.FaceStudioDocument
import com.healthsync.phone.data.watchface.FaceStudioFileStore
import com.healthsync.phone.data.watchface.FaceStudioHistory
import com.healthsync.phone.data.watchface.FaceStudioTemplates
import com.healthsync.phone.data.watchface.OnlineWatchFaceCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FaceStudioDocumentTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun blankDocumentsHaveDifferentIdsAndAppropriateClocks() {
        val analog = FaceStudioDocument.blank("analog")
        val digital = FaceStudioDocument.blank("digital")
        val hybrid = FaceStudioDocument.blank("hybrid")
        assertEquals(3, setOf(analog.id, digital.id, hybrid.id).size)
        assertNotNull(analog.hands)
        assertNull(analog.digitalClock)
        assertNull(digital.hands)
        assertTrue(digital.digitalClock!!.enabled)
        assertNotNull(hybrid.hands)
        assertNotNull(hybrid.digitalClock)
        listOf(analog, digital, hybrid).forEach { assertTrue(FaceStudioDocument.validationErrors(it).toString(), FaceStudioDocument.validationErrors(it).isEmpty()) }
    }

    @Test fun duplicateRegeneratesFaceAndLayerIdsWithoutChangingDesign() {
        val source = FaceStudioDocument.blank().copy(layers = listOf(FaceLayer(id = "title", text = "DESIGN"), FaceLayer(id = "shape", type = "circle")))
        val duplicate = FaceStudioDocument.duplicate(source)
        assertNotEquals(source.id, duplicate.id)
        assertEquals("${source.name} Copy", duplicate.name)
        assertEquals(source.background, duplicate.background)
        assertEquals("DESIGN", duplicate.layers.first().text)
        assertEquals(2, duplicate.layers.map { it.id }.distinct().size)
        assertTrue(duplicate.layers.none { newLayer -> source.layers.any { it.id == newLayer.id } })
    }

    @Test fun packageRoundTripsAllLayersAndReportsUtf8Size() {
        val source = FaceStudioDocument.blank("digital").copy(name = "सूर्योदय ✨", layers = listOf(FaceLayer(id = "label", text = "TEST", rotationDegrees = 32f, showInAmbient = true)))
        val json = FaceStudioDocument.exportJson(source)
        val imported = FaceStudioDocument.importJson(json)
        assertTrue(imported.errors.toString(), imported.isSuccess)
        assertEquals(source, imported.packageOrNull)
        assertEquals(json.toByteArray(Charsets.UTF_8).size, FaceStudioDocument.estimatedBytes(source))
        assertTrue(FaceStudioDocument.estimatedBytes(source) > json.length)
    }

    @Test fun oldMinimalFilesReceiveAllNestedDefaults() {
        val result = FaceStudioDocument.importJson("""{"id":"old_face","name":"Old face","background":{"type":"solid"},"hands":{"hourHand":{"shape":"baton"}},"digitalClock":{"fontSizeSp":42},"layers":[{"text":"LEGACY"}]}""")
        assertTrue(result.errors.toString(), result.isSuccess)
        val pkg = result.packageOrNull!!
        assertEquals("#0A0D12", pkg.background.colorHex)
        assertEquals("baton", pkg.hands!!.hourHand.shape)
        assertNotNull(pkg.hands.minuteHand)
        assertTrue(pkg.digitalClock!!.enabled)
        assertEquals("HH:mm", pkg.digitalClock.format)
        assertEquals(42f, pkg.digitalClock.fontSizeSp, 0f)
        assertEquals("text", pkg.layers.first().type)
        assertTrue(FaceStudioDocument.isSafeId(pkg.layers.first().id))
        val minimal = FaceStudioDocument.importJson("""{"id":"minimal","name":"Minimal"}""").packageOrNull!!
        assertTrue(minimal.layers.isEmpty())
        assertNotNull(minimal.aod)
        assertNotNull(minimal.complications.heartRate)
    }

    @Test fun malformedFieldsCannotCreateNullOrCoercedConfigurations() {
        val fragments = listOf(
            "\"background\":null", "\"dial\":\"bad\"", "\"aod\":false", "\"complications\":null",
            "\"hands\":{\"hourHand\":null}", "\"digitalClock\":{\"enabled\":\"true\"}",
            "\"layers\":[null]", "\"layers\":[{\"name\":null}]", "\"name\":27",
            "\"schemaVersion\":1.5", "\"type\":\"unknown\"", "\"layers\":[{\"type\":\"unknown\"}]"
        )
        fragments.forEach { fragment ->
            val json = if (fragment.startsWith("\"name\"")) "{\"id\":\"face\",$fragment}" else "{\"id\":\"face\",\"name\":\"Face\",$fragment}"
            val result = FaceStudioDocument.importJson(json)
            assertFalse(fragment, result.isSuccess)
            assertTrue(fragment, result.errors.isNotEmpty())
        }
        assertFalse(FaceStudioDocument.importJson("""{"name":"No ID"}""").isSuccess)
        assertFalse(FaceStudioDocument.importJson("""{"id":"id","name":""}""").isSuccess)
    }

    @Test fun importRejectsNonFiniteUnsupportedAndOutOfRangeValues() {
        val fragments = listOf(
            "\"schemaVersion\":2", "\"background\":{\"gradientAngle\":361}",
            "\"dial\":{\"tickCount\":13}", "\"digitalClock\":{\"fontSizeSp\":1e40}",
            "\"layers\":[{\"opacity\":1.5}]", "\"layers\":[{\"xRatio\":NaN}]",
            "\"layers\":[{\"xRatio\":1e999}]", "\"layers\":[{\"colorHex\":\"red\"}]",
            "\"interactive\":{\"colorPalettes\":[false]}", "\"date\":{\"format\":\"BOGUS\"}"
        )
        fragments.forEach { fragment -> assertFalse(fragment, FaceStudioDocument.importJson("{\"id\":\"face\",\"name\":\"Face\",$fragment}").isSuccess) }
        assertFalse(FaceStudioDocument.importJson("""{"id":"face","name":"Face","name":"Duplicate"}""").isSuccess)
        assertFalse(FaceStudioDocument.importJson("""{"id":"face","name":"Face"} garbage""").isSuccess)
        assertFalse(FaceStudioDocument.importJson("{unquoted: true}").isSuccess)
        assertFalse(FaceStudioDocument.importJson("[]").isSuccess)
    }

    @Test fun unsafeIdsAndDuplicateLayerIdsAreRejected() {
        listOf("../outside", "..", "sub/path", "sub\\path", "C:drive", "CON", "NUL", "", "a".repeat(129)).forEach { id -> assertFalse(id, FaceStudioDocument.isSafeId(id)) }
        assertTrue(FaceStudioDocument.isSafeId("custom_abc-123"))
        val duplicate = FaceStudioDocument.blank().copy(layers = listOf(FaceLayer(id = "same"), FaceLayer(id = "same")))
        assertTrue(FaceStudioDocument.validationErrors(duplicate).any { it.contains("unique ID") })
        val unsafe = FaceStudioDocument.blank().copy(id = "../outside")
        assertFalse(FaceStudioDocument.importJson("""{"id":"../outside","name":"Bad"}""").isSuccess)
        assertTrue(FaceStudioDocument.validationErrors(unsafe).isNotEmpty())
    }

    @Test fun oversizeInputAndTooManyLayersFailClearly() {
        val result = FaceStudioDocument.importJson(" ".repeat(FaceStudioDocument.MAX_PACKAGE_BYTES + 1))
        assertFalse(result.isSuccess)
        assertTrue(result.errors.first().contains("512 KB"))
        val source = FaceStudioDocument.blank().copy(layers = (0..FaceStudioDocument.MAX_LAYERS).map { FaceLayer(id = "layer_$it") })
        assertTrue(FaceStudioDocument.validationErrors(source).any { it.contains("128 layers") })
        val multibyte = "文".repeat(FaceStudioDocument.MAX_PACKAGE_BYTES / 2)
        assertTrue(FaceStudioDocument.importJson(multibyte).errors.first().contains("512 KB"))
    }

    @Test fun historyUndoRedoAndNewEditDiscardRedoBranch() {
        val initial = FaceStudioDocument.blank()
        val history = FaceStudioHistory(initial)
        history.commit(initial.copy(name = "First"))
        history.commit(initial.copy(name = "Second"))
        assertEquals("First", history.undo().name)
        assertTrue(history.canRedo)
        assertEquals("Second", history.redo().name)
        history.undo()
        history.commit(initial.copy(name = "Branch"))
        assertFalse(history.canRedo)
        assertEquals("First", history.undo().name)
        assertEquals("Branch", history.redo().name)
    }

    @Test fun gestureUpdatesBecomeOneUndoAndCancelledGestureKeepsRedo() {
        val initial = FaceStudioDocument.blank().copy(layers = listOf(FaceLayer(id = "layer")))
        val history = FaceStudioHistory(initial)
        history.beginGesture()
        (1..20).forEach { step -> history.commit(initial.copy(layers = listOf(initial.layers.first().copy(xRatio = step / 20f)))) }
        assertTrue(history.canUndo)
        history.endGesture()
        assertEquals(initial, history.undo())
        assertFalse(history.canUndo)
        assertEquals(1f, history.redo().layers.first().xRatio, 0f)
        history.undo()
        history.beginGesture()
        history.commit(initial.copy(name = "Cancelled"))
        assertEquals(initial, history.cancelGesture())
        assertTrue(history.canRedo)
    }

    @Test fun historyIsBoundedAndProtectsAgainstMutableInputLists() {
        val initial = FaceStudioDocument.blank()
        val history = FaceStudioHistory(initial, capacity = 2)
        (1..4).forEach { history.commit(initial.copy(name = "$it")) }
        assertEquals("3", history.undo().name)
        assertEquals("2", history.undo().name)
        assertFalse(history.canUndo)
        val mutable = mutableListOf(FaceLayer(id = "one"))
        history.commit(initial.copy(layers = mutable))
        mutable.clear()
        assertEquals(1, history.current.layers.size)
        try {
            (history.current.layers as MutableList).clear()
            fail("A history snapshot must be immutable")
        } catch (_: UnsupportedOperationException) { }
        history.reset(initial)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test fun largeImageDocumentsRespectTheCombinedHistoryByteBudget() {
        val image = java.util.Base64.getEncoder().encodeToString(ByteArray(260_000) { (it % 255).toByte() })
        val initial = FaceStudioDocument.blank().let { it.copy(background = it.background.copy(type = "image", imageBase64 = image)) }
        val history = FaceStudioHistory(initial, capacity = 80, maxRetainedBytes = 700_000)
        (1..6).forEach { history.commit(initial.copy(name = "Photo $it")) }
        assertTrue(history.retainedBytes <= 700_000)
        assertEquals("Photo 5", history.undo().name)
        assertTrue(history.retainedBytes <= 700_000)
        assertEquals("Photo 4", history.undo().name)
        assertFalse(history.canUndo)
        assertEquals("Photo 5", history.redo().name)
        assertTrue(history.retainedBytes <= 700_000)
        assertEquals("Photo 6", history.redo().name)
        history.reset(initial)
        assertEquals(0L, history.retainedBytes)
    }

    @Test fun storeIsAtomicPathSafeAndDraftIsSeparateFromSavedLibrary() {
        val dir = temporary.newFolder("faces")
        val store = FaceStudioFileStore(dir)
        val initial = FaceStudioDocument.blank().copy(name = "Saved")
        assertTrue(store.save(initial))
        assertEquals(initial, store.load(initial.id))
        assertTrue(store.saveDraft(initial.copy(name = "Recovery")))
        assertEquals("Recovery", store.loadDraft()!!.name)
        assertEquals(listOf(initial), store.list())
        assertFalse(store.save(initial.copy(id = "../outside")))
        assertFalse(store.delete("../outside"))
        assertNull(store.load("../outside"))
        assertFalse(File(dir.parentFile, "outside.hswf").exists())
        assertFalse(store.save(initial.copy(name = "")))
        assertEquals("Saved", store.load(initial.id)!!.name)
        assertTrue(store.save(initial.copy(name = "Updated")))
        assertEquals("Updated", store.load(initial.id)!!.name)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        assertTrue(store.clearDraft())
        assertNull(store.loadDraft())
        assertTrue(store.delete(initial.id))
        assertNull(store.load(initial.id))
    }

    @Test fun storeSkipsMalformedMismatchedAndOversizeFiles() {
        val dir = temporary.newFolder("invalid_faces")
        val store = FaceStudioFileStore(dir)
        File(dir, "bad.hswf").writeText("broken")
        File(dir, "large.hswf").writeText(" ".repeat(FaceStudioDocument.MAX_PACKAGE_BYTES + 1))
        File(dir, "mismatch.hswf").writeText(FaceStudioDocument.exportJson(FaceStudioDocument.blank()))
        assertNull(store.load("bad"))
        assertNull(store.load("large"))
        assertNull(store.load("mismatch"))
        assertTrue(store.list().isEmpty())
    }

    @Test fun individualProjectDraftsSurviveWhenAnotherProjectBecomesLatest() {
        val dir = temporary.newFolder("project_drafts")
        val store = FaceStudioFileStore(dir)
        val faceA = FaceStudioDocument.blank().copy(name = "Project A")
        val faceB = FaceStudioDocument.blank().copy(name = "Project B")
        assertTrue(store.save(faceA))
        assertTrue(store.saveDraft(faceA.copy(name = "Recovered A")))
        assertTrue(store.saveDraft(faceB.copy(name = "Recovered B")))
        assertEquals("Recovered B", store.loadDraft()!!.name)
        assertEquals("Recovered A", store.loadDraft(faceA.id)!!.name)
        assertEquals("Recovered B", store.loadDraft(faceB.id)!!.name)
        assertNull(store.loadDraft("../outside"))
        assertEquals(listOf(faceA), store.list())
        assertTrue(store.clearDraft())
        assertNull(store.loadDraft())
        assertNull(store.loadDraft(faceA.id))
        assertNull(store.loadDraft(faceB.id))
        assertEquals(faceA, store.load(faceA.id))
    }

    @Test fun recoveryKeepsAtMostTwentyProjectDrafts() {
        val dir = temporary.newFolder("bounded_drafts")
        val store = FaceStudioFileStore(dir)
        val projects = (1..22).map { FaceStudioDocument.blank().copy(name = "Project $it") }
        projects.forEachIndexed { index, pkg ->
            assertTrue(store.saveDraft(pkg))
            // Deterministic timestamps make eviction ordering independent of filesystem precision.
            File(dir, ".studio-draft-${pkg.id}.hswf").setLastModified(1_000L + index)
        }
        assertEquals(20, dir.listFiles()!!.count { it.name.startsWith(".studio-draft-") })
        assertEquals(projects.last(), store.loadDraft())
        assertNull(store.loadDraft(projects.first().id))
        assertNotNull(store.loadDraft(projects.last().id))
        assertTrue(store.clearDraft())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun preflightReportsMissingClockAndCircularClipping() {
        val pkg = FaceStudioDocument.blank("digital").copy(digitalClock = null, layers = listOf(FaceLayer(id = "edge", name = "Edge title", xRatio = 0.98f)))
        assertTrue(FaceStudioDocument.warnings(pkg).any { it.contains("no visible clock") })
        assertTrue(FaceStudioDocument.warnings(pkg).any { it.contains("Edge title") && it.contains("clipped") })
        val dense = FaceStudioDocument.blank().copy(layers = (1..20).map { FaceLayer(id = "dense_$it", text = "W".repeat(256), fontSizeSp = 128f) })
        val warnings = FaceStudioDocument.warnings(dense)
        assertTrue(warnings.any { it.contains("rendering performance") })
        assertTrue(warnings.any { it.contains("clipped") })
        assertTrue(FaceStudioDocument.validationErrors(dense.copy(layers = listOf(FaceLayer(text = "X".repeat(257))))).any { it.contains("256") })
        assertTrue(FaceStudioDocument.validationErrors(dense.copy(layers = listOf(FaceLayer(fontSizeSp = 129f)))).any { it.contains("128") })
    }

    @Test fun everyActualStudioStarterAndCatalogFacePassesProductionValidation() {
        val starters = FaceStudioTemplates.all()
        assertEquals(6, starters.size)
        assertEquals(setOf("Photo Memories", "Executive Chrono", "Cyber Neon", "Minimal Bauhaus", "Velocity Sport", "Digital Retro 1991"), starters.map { it.name }.toSet())
        val catalog = OnlineWatchFaceCatalog.items.map { it.pkg }
        assertTrue("Validate the whole live catalog", catalog.size >= 60)
        (starters + catalog).forEach { pkg ->
            val errors = FaceStudioDocument.validationErrors(pkg)
            assertTrue("${pkg.id}: $errors", errors.isEmpty())
            val exported = FaceStudioDocument.exportJson(pkg)
            val imported = FaceStudioDocument.importJson(exported)
            assertTrue("${pkg.id}: ${imported.errors}", imported.isSuccess)
            assertEquals("${pkg.id} must survive production import unchanged", pkg, imported.packageOrNull)
        }
    }
}
