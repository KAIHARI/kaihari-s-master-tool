package com.kaiharimoto.mastertool.core.tune

import com.kaiharimoto.mastertool.core.layout.CameraEnvelope
import com.kaiharimoto.mastertool.core.layout.StageSeat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * That the tuning document is safe to hand a slider and safe to read off disk.
 *
 * The claims fall in three groups, and all three are about the same worry: this
 * is the one object in the app whose values arrive from a finger and a file
 * rather than from a programmer, so every guard it has is the only guard there
 * is.
 */
class StageTuningTest {

    // ---- the default is what shipped ---------------------------------------------------

    @Test
    fun theDefaultCameraIsTheSeatTheStageAlreadyOpenedAt() {
        // The whole reason this can land in a release before the panel does.
        assertEquals(StageSeat.TABLE.pose, StageTuning.DEFAULT.camera.pose())
        assertTrue(StageTuning.DEFAULT.isDefault)
    }

    @Test
    fun theDefaultIsTheNumbersTheConstantsHad() {
        // Typed out rather than derived, deliberately: this test is the record
        // of what the play stage shipped with, and a version of it that read the
        // same constants the document does would agree with itself and with
        // nothing else.
        val d = StageTuning.DEFAULT
        assertEquals(-24f, d.hand.leanDegrees)
        assertEquals(1f, d.hand.liftFactor)
        assertEquals(0.62f, d.hand.stepFraction)
        assertEquals(1.6f, d.hand.liftRatio)
        assertEquals(0.55f, d.cards.carryLift)
        assertEquals(0.85f, d.cards.fanLiftRatio)
        assertEquals(1.35f, d.cards.peekLift)
        assertEquals(1.9f, d.cards.peekScale)
        // And the one thing that must be off, or the tool changes the picture
        // before anybody has touched it.
        assertEquals(0f, d.focus.strength)
    }

    @Test
    fun sanitisingWhatShippedChangesNothing() {
        // It runs on load and on save, so a default that its own clamp would
        // move is a document that rewrites itself on first launch.
        assertEquals(StageTuning.DEFAULT, StageTuning.DEFAULT.sanitised())
    }

    // ---- every knob is well formed ------------------------------------------------------

    @Test
    fun everyKnobsDefaultSitsInsideItsOwnRange() {
        // A slider that opens outside its own track snaps the moment it is
        // touched, and the user sees a value they did not set.
        StageKnobs.ALL.forEach { knob ->
            val value = knob.get(StageTuning.DEFAULT)
            assertTrue(
                value in knob.min..knob.max,
                "${knob.path} ships at $value, outside ${knob.min}..${knob.max}",
            )
            assertTrue(knob.max > knob.min, "${knob.path} has an empty range")
            assertTrue(knob.step > 0f && knob.step <= knob.max - knob.min, "${knob.path} step")
        }
    }

    @Test
    fun everyKnobReadsBackWhatItWrites() {
        // get/set are two lambdas per knob and nothing but this stops one of
        // them pointing at the wrong field — a copy-paste error that shows up as
        // one slider moving another.
        StageKnobs.ALL.forEach { knob ->
            val target = knob.valueAt(0.73f)
            val written = knob.set(StageTuning.DEFAULT, target)
            assertEquals(target, knob.get(written), "${knob.path} did not read back")
            // And it moved nothing else.
            StageKnobs.ALL.filter { it.path != knob.path }.forEach { other ->
                assertEquals(
                    other.get(StageTuning.DEFAULT),
                    other.get(written),
                    "setting ${knob.path} also moved ${other.path}",
                )
            }
        }
    }

    @Test
    fun everyKnobHasItsOwnPathAndAReadableNote() {
        val paths = StageKnobs.ALL.map { it.path }
        assertEquals(paths.size, paths.toSet().size, "two knobs share a path: $paths")
        StageKnobs.ALL.forEach {
            assertTrue(it.label.isNotBlank(), "${it.path} has no label")
            assertTrue(it.note.length > 20, "${it.path}'s note says nothing: '${it.note}'")
            assertTrue(it.group in StageKnobs.GROUPS, "${it.path} is in no group")
        }
        // Every group has something in it, or the panel draws an empty heading.
        StageKnobs.GROUPS.forEach { assertTrue(StageKnobs.of(it).isNotEmpty(), "$it is empty") }
    }

    @Test
    fun theCameraKnobsDoNotOfferAPoseTheEnvelopeWouldRefuse() {
        // A slider that reaches past the envelope reads as broken: you drag it,
        // the clamp pulls it back, and nothing you do moves it further.
        val envelope = CameraEnvelope()
        val pitch = StageKnobs.ALL.first { it.path == "camera.pitchDegrees" }
        val lens = StageKnobs.ALL.first { it.path == "camera.lens" }

        assertEquals(envelope.minPitch, pitch.min)
        assertEquals(envelope.maxPitch, pitch.max)
        assertEquals(envelope.minLens, lens.min)
        assertEquals(envelope.maxLens, lens.max)
    }

    // ---- and nothing hostile survives the door -------------------------------------------

    @Test
    fun aNonFiniteValueIsRefusedRatherThanStored() {
        // The one that breaks the stage *after a restart*, with no cause visible
        // and no way back: NaN reaches `StagePlane`'s trigonometry and every
        // trig function downstream returns NaN, so nothing draws and nothing logs.
        StageKnobs.ALL.forEach { knob ->
            listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { bad ->
                val written = knob.set(StageTuning.DEFAULT, bad)
                assertTrue(
                    knob.get(written).isFinite(),
                    "${knob.path} stored $bad",
                )
                assertEquals(
                    knob.get(StageTuning.DEFAULT),
                    knob.get(written),
                    "${knob.path} did not fall back to what ships",
                )
            }
        }
    }

    @Test
    fun aDocumentFullOfNonsenseSanitisesToSomethingDrawable() {
        val wrecked = StageTuning(
            camera = CameraTune(Float.NaN, 900f, -3f, 0f),
            hand = HandTune(90f, 0.01f, 40f, -2f),
            cards = CardTune(-9f, Float.POSITIVE_INFINITY, 0f, 0f),
            focus = FocusTune(17f, -4f, 8f),
        ).sanitised()

        StageKnobs.ALL.forEach { knob ->
            val value = knob.get(wrecked)
            assertTrue(value.isFinite(), "${knob.path} came back $value")
            assertTrue(value in knob.min..knob.max, "${knob.path} came back $value")
        }
        // And the hand's lift never goes under the felt, which is the one that
        // folds a shadow through itself rather than merely looking wrong.
        assertTrue(wrecked.hand.liftFactor >= 1f)
    }

    // ---- the export ----------------------------------------------------------------------

    @Test
    fun anUntouchedExportSaysNothingChanged() {
        val text = TuningCodec.export(StageTuning.DEFAULT)

        assertTrue(TuningCodec.changedIn(StageTuning.DEFAULT).isEmpty())
        assertTrue(text.contains("\"changed\": []"), text.take(400))
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test
    fun anExportNamesExactlyWhatWasMoved() {
        val tuned = StageTuning.DEFAULT
            .let { StageKnobs.ALL.first { k -> k.path == "camera.lens" }.set(it, 1.34f) }
            .let { StageKnobs.ALL.first { k -> k.path == "hand.leanDegrees" }.set(it, -31f) }

        val changed = TuningCodec.changedIn(tuned)
        assertEquals(2, changed.size, "$changed")
        assertTrue(changed.any { it.startsWith("camera.lens: 1.34") }, "$changed")
        assertTrue(changed.any { it.startsWith("hand.leanDegrees: -31") }, "$changed")
    }

    @Test
    fun anExportReadsBackAsTheSameDocument() {
        // The round trip is the contract with the person pasting it: what they
        // hand back has to mean what the panel showed them.
        val tuned = StageKnobs.ALL.fold(StageTuning.DEFAULT) { d, knob ->
            knob.set(d, knob.valueAt(0.42f))
        }

        val text = TuningCodec.export(tuned, TuningSurface(2960, 1848, 2f, "DESK", "NIGHT", "TABLE"))
        assertEquals(tuned, TuningCodec.parse(text))
    }

    @Test
    fun aBareDocumentPastedOnItsOwnIsAlsoRead() {
        // Somebody will trim the export down to the interesting half. A tool
        // that only accepts its own whole output is a tool that argues.
        val bare = """{"camera":{"lens":1.5},"hand":{"leanDegrees":-30}}"""
        val read = assertNotNull(TuningCodec.parse(bare))

        assertEquals(1.5f, read.camera.lens)
        assertEquals(-30f, read.hand.leanDegrees)
        // Everything unmentioned is what ships, not zero.
        assertEquals(StageTuning.DEFAULT.cards, read.cards)
    }

    @Test
    fun rubbishFromAClipboardIsRefusedRatherThanThrown() {
        listOf("", "   ", "not json", "{", "[1,2,3]").forEach {
            assertNull(TuningCodec.parse(it), "parsed '$it'")
        }
    }

    @Test
    fun aPastedDocumentIsClampedLikeAnyOther() {
        val hostile = """{"camera":{"pitchDegrees":900,"lens":-4},"focus":{"strength":9}}"""
        val read = assertNotNull(TuningCodec.parse(hostile))

        assertEquals(CameraEnvelope().maxPitch, read.camera.pitchDegrees)
        assertEquals(CameraEnvelope().minLens, read.camera.lens)
        assertEquals(0.25f, read.focus.strength)
    }

    @Test
    fun theReferenceBlockIsReadOffTheLiveConstants() {
        // A reference table that lies is worse than no reference table, so it is
        // derived rather than typed. This is the guard against somebody later
        // "tidying" it into literals.
        val reference = StageReference()

        assertEquals(CameraEnvelope().maxPitch, reference.envelope.maxPitch)
        assertEquals(StageSeat.entries.size, reference.seats.size)
        StageSeat.entries.forEach {
            assertEquals(CameraTune.of(it.pose), reference.seats.getValue(it.name))
        }
    }

    @Test
    fun aNumberIsWrittenAsShortAsItCanBe() {
        // `0.62f.toString()` is "0.62" on JVM and a float's true expansion
        // elsewhere; an export full of 0.6200000047683716 reads as precision
        // that is not there.
        assertEquals("0.62", TuningCodec.trim(0.62f))
        assertEquals("-24", TuningCodec.trim(-24f))
        assertEquals("1", TuningCodec.trim(1f))
        assertEquals("1.34", TuningCodec.trim(1.34f))
        assertEquals("0", TuningCodec.trim(Float.NaN))
    }
}
