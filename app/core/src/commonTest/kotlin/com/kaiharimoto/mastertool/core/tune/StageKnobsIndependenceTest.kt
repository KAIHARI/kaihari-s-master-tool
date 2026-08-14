package com.kaiharimoto.mastertool.core.tune

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That moving one knob moves one knob.
 *
 * The panel shipped for three releases with every slider resetting every other
 * slider, and the fault was not in any of these setters — it was a step further
 * out, in a `pointerInput` that captured the whole document and then outlived
 * the composition it captured it from. `StageTuner`'s own KDoc carries that
 * story, because it is a Compose fact and it belongs where the Compose is.
 *
 * What belongs *here* is the claim the repair rests on: that `Knob.set` applied
 * to the live document is a safe way to write one field, whichever knob it is
 * and whatever else has been moved. If that is not true then handing the panel a
 * `(Knob, Float)` fixes nothing, and a copy-and-paste in one of the setter
 * lambdas — `d.copy(room = d.room.copy(windowSill = v))` under the wrong label —
 * would reproduce the same symptom through an entirely different cause.
 *
 * These are cheap and they are quadratic on purpose. The panel is thirty-one
 * knobs now and its square is under a thousand writes, which is nothing, and the
 * pairwise form is what catches a setter that reads the right field and writes
 * its neighbour. The count is deliberately not asserted anywhere: a test that
 * had to be edited every time a knob was added is a test people learn to edit
 * without reading.
 */
class StageKnobsIndependenceTest {

    /**
     * A value inside [Knob]'s range that is not the default and lands on a step.
     *
     * Two thirds of the way along rather than the middle, because a handful of
     * these knobs default to their own midpoint and a test that moved a value to
     * where it already was would pass without asserting anything.
     */
    private fun Knob.probe(): Float {
        val candidate = valueAt(2f / 3f)
        return if (candidate != get(StageTuning.DEFAULT)) candidate else valueAt(1f / 3f)
    }

    @Test
    fun everyKnobHasSomewhereToMoveTo() {
        // The premise of everything below. A knob whose probe is its default
        // would make both tests vacuous for that row.
        StageKnobs.ALL.forEach { knob ->
            assertTrue(
                knob.probe() != knob.get(StageTuning.DEFAULT),
                "${knob.path} has no in-range value that differs from its default",
            )
        }
    }

    @Test
    fun everyKnobSurvivesEveryOtherKnobBeingMoved() {
        // The panel, used the way a person uses it: move everything, one after
        // another, and expect to find all of it still moved.
        val tuned = StageKnobs.ALL.fold(StageTuning.DEFAULT) { document, knob ->
            knob.set(document, knob.probe())
        }

        StageKnobs.ALL.forEach { knob ->
            assertEquals(
                knob.probe(),
                knob.get(tuned),
                "${knob.path} did not survive the knobs after it",
            )
        }
    }

    @Test
    fun writingOneKnobMovesExactlyOneKnob() {
        // And the same claim pairwise, which is the one that names the culprit
        // rather than merely reporting that something went missing.
        val tuned = StageKnobs.ALL.fold(StageTuning.DEFAULT) { document, knob ->
            knob.set(document, knob.probe())
        }

        StageKnobs.ALL.forEach { written ->
            val after = written.set(tuned, written.get(StageTuning.DEFAULT))
            StageKnobs.ALL.forEach { other ->
                if (other.path == written.path) return@forEach
                assertEquals(
                    other.get(tuned),
                    other.get(after),
                    "writing ${written.path} moved ${other.path}",
                )
            }
        }
    }

    @Test
    fun theOrderKnobsAreWrittenInDoesNotMatter() {
        val forwards = StageKnobs.ALL.fold(StageTuning.DEFAULT) { d, k -> k.set(d, k.probe()) }
        val backwards = StageKnobs.ALL.reversed().fold(StageTuning.DEFAULT) { d, k -> k.set(d, k.probe()) }

        assertEquals(forwards, backwards, "the document depends on the order its sliders were dragged")
    }

    @Test
    fun aTunedDocumentIsAlreadySanitised() {
        // `sanitised` runs on every write and on every load, so a value that
        // came off a slider must be a fixed point of it. If it is not, the panel
        // fights the preference store and a knob creeps every time anything else
        // is touched.
        val tuned = StageKnobs.ALL.fold(StageTuning.DEFAULT) { d, k -> k.set(d, k.probe()) }

        assertEquals(tuned, tuned.sanitised())
        assertEquals(tuned.sanitised(), tuned.sanitised().sanitised())
    }

    @Test
    fun aKnobShovedPastEitherEndLandsOnItAndTakesNothingWithIt() {
        // What a fat finger at the end of a track does, and what a hand-edited
        // JSON does. Both arrive here.
        val tuned = StageKnobs.ALL.fold(StageTuning.DEFAULT) { d, k -> k.set(d, k.probe()) }

        StageKnobs.ALL.forEach { knob ->
            listOf(-1e9f, 1e9f, Float.NaN, Float.POSITIVE_INFINITY).forEach { rubbish ->
                val after = knob.set(tuned, rubbish)
                assertTrue(
                    knob.get(after) in knob.min..knob.max,
                    "${knob.path} kept $rubbish as ${knob.get(after)}",
                )
                StageKnobs.ALL.forEach { other ->
                    if (other.path == knob.path) return@forEach
                    assertEquals(
                        other.get(tuned),
                        other.get(after),
                        "writing $rubbish to ${knob.path} moved ${other.path}",
                    )
                }
            }
        }
    }
}
