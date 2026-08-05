package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.Projected
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.Vec3
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * One fixed scene, three camera poses, written down.
 *
 * This is the only recording of computed output in the repository and it is
 * meant to stay the only one. `YdkCodecTest` also holds text in a `trimIndent`,
 * but that text is a *format* — somebody else's specification, which the app
 * does not get a vote on — and comparing against it is an ordinary claim.
 *
 * A golden is not. Every other test here is an argument: it names something
 * about the domain in its own name, and when it fails it says which claim
 * stopped being true. A golden names nothing. It asserts that today equals
 * yesterday, which is not a claim about cards or light at all, and a suite full
 * of them is a suite nobody reads and everybody regenerates.
 *
 * It earns its one place because of the shape of the renderer. Six small
 * pieces — [Rot3], [CardSolid], [Shading], [Shadows], [StageRig] and
 * [StagePlane] under a [CameraPose] — are each tested thoroughly *alone* and
 * none of them is tested *composed*. Their property tests are all local, and
 * deliberately so: a shadow moves the way the light pushes it, a pile never
 * becomes a tower, the near edge of a deck is lit by the fill. Every one of
 * those keeps passing while a projection constant, the tilt, the clearance in
 * [com.kaiharimoto.mastertool.core.layout.CameraEnvelope] or the pile curve
 * moves every card on the table by four pixels. The composition is what ships
 * to the tablet, and this is the only test that looks at it.
 *
 * So what it buys is not correctness — the tests around it own that — but
 * *notice*. Change a term in the renderer and this fails with the object and
 * the field that moved, next to the whole new dump ready to paste. Change one
 * by accident and it fails the same way, which is the entire point.
 *
 * It is not a licence to stop writing the other kind. A change that only this
 * test catches is a change whose meaning nobody has stated yet: the right
 * answer to a red line here is usually to write the property test that says
 * why the new number is right, and then re-record this.
 *
 * There is no `assertClose` in this file, and its absence is the design rather
 * than an omission: every number is quantised to a grid on the way into the
 * text, and that grid *is* the tolerance. See the formatter at the foot of the
 * file, which is the one part of this worth reading twice.
 */
class GoldenStageTest {

    // ---- the goldens --------------------------------------------------------------

    @Test
    fun theSceneFromTheHomeSeatIsWhatItWasWhenThisWasRecorded() {
        assertGolden(
            seat = "home",
            expected = """
            rest face   364.3,571.4  485.9,571.4  475.7,749.6  350.2,749.6
            rest solid  near 0.791/-0.067 413.0,749.6  front 1.000/0.099 419.1,659.1
            rest shadow 364.3,571.4  485.9,571.4  475.7,749.6  350.2,749.6  dark 0.660  soft 3.1
            rest shade  diff 0.955  spec 0.225  rim 0.000  hot 0.309,0.378
            held face   860.5,261.1  968.1,264.4  965.9,443.1  859.7,446.9
            held solid  far 0.927/0.159 915.2,262.8  left 0.895/0.106 859.9,354.6  front 0.920/0.006 914.6,354.5
            held shadow 907.5,394.1  992.9,351.0  975.2,471.6  887.6,516.7  dark 0.351  soft 34.4
            held shade  diff 0.837  spec 0.000  rim 0.002  hot -0.383,-0.228
            set  face   1130.7,628.6  1138.1,752.8  1320.2,752.8  1308.8,628.6
            set  solid  back 1.000/0.099 1224.6,689.9  right 0.791/-0.067 1229.2,752.7
            set  shadow 1130.7,628.6  1138.1,752.8  1320.2,752.8  1308.8,628.6  dark 0.660  soft 3.1
            set  shade  diff 0.955  spec 0.132  rim 0.000  hot 0.622,0.309
            pile face   293.9,174.5  408.5,174.5  396.5,329.9  278.4,329.9
            pile solid  near 0.791/-0.067 340.8,334.0  front 1.000/0.099 344.5,251.1
            pile shadow 307.3,194.5  420.6,194.5  409.1,348.7  292.5,348.7  dark 0.545  soft 10.6
            pile shade  diff 0.955  spec 0.207  rim 0.000  hot 0.309,0.378
            """.trimIndent(),
            actual = dump(Home),
        )
    }

    @Test
    fun theSceneWithTheTableTurnedIsWhatItWasWhenThisWasRecorded() {
        assertGolden(
            seat = "turned",
            expected = """
            rest face   488.2,830.3  591.4,751.0  701.0,899.9  596.5,983.4
            rest solid  near 0.798/-0.071 649.2,941.3  left 0.804/0.078 541.7,905.8  front 1.000/0.099 593.9,864.9
            rest shadow 488.2,830.3  591.4,751.0  701.0,899.9  596.5,983.4  dark 0.660  soft 3.1
            rest shade  diff 0.955  spec 0.089  rim 0.000  hot 0.207,0.343
            held face   721.5,266.8  798.9,211.9  898.8,356.9  828.0,418.1
            held solid  far 0.927/0.159 761.0,238.8  left 0.895/0.106 775.1,343.3  front 0.923/0.004 813.2,313.4
            held shadow 817.0,353.7  856.2,271.4  917.8,375.7  878.8,461.4  dark 0.351  soft 34.4
            held shade  diff 0.837  spec 0.000  rim 0.009  hot -0.574,-0.330
            set  face   1128.9,408.4  1208.3,498.2  1335.1,396.7  1255.1,310.3
            set  solid  back 1.000/0.099 1232.3,402.4  far 0.804/0.078 1168.3,452.9  right 0.798/-0.071 1272.4,446.9
            set  shadow 1128.9,408.4  1208.3,498.2  1335.1,396.7  1255.1,310.3  dark 0.660  soft 3.1
            set  shade  diff 0.955  spec 0.107  rim 0.000  hot 0.657,0.207
            pile face   153.1,546.5  257.3,473.7  354.6,610.3  249.1,686.9
            pile solid  near 0.798/-0.071 306.0,650.2  left 0.804/0.078 205.0,617.8  front 1.000/0.099 253.3,578.2
            pile shadow 175.8,555.9  278.3,484.1  374.7,619.0  271.0,694.6  dark 0.545  soft 10.6
            pile shade  diff 0.955  spec 0.120  rim 0.000  hot 0.207,0.343
            """.trimIndent(),
            actual = dump(Turned),
        )
    }

    @Test
    fun theSceneFromALowSteepSeatIsWhatItWasWhenThisWasRecorded() {
        assertGolden(
            seat = "steep",
            expected = """
            rest face   464.1,535.1  557.8,535.1  543.4,625.9  444.0,625.9
            rest solid  near 0.749/-0.037 493.7,626.1  front 1.000/0.094 502.6,579.2
            rest shadow 464.1,535.1  557.8,535.1  543.4,625.9  444.0,625.9  dark 0.660  soft 3.1
            rest shade  diff 0.955  spec 0.069  rim 0.069  hot 0.263,0.767
            held face   842.1,324.9  919.6,348.5  923.9,456.2  843.7,433.7
            held solid  near 0.734/-0.011 884.1,445.2  left 0.895/0.106 842.8,378.4  front 0.925/0.003 882.7,390.0
            held shadow 880.8,449.2  944.2,429.0  933.1,486.2  867.0,508.1  dark 0.351  soft 34.4
            held shade  diff 0.837  spec 0.000  rim 0.014  hot -0.414,0.299
            set  face   1057.1,563.7  1067.7,627.6  1211.9,627.6  1195.6,563.7
            set  solid  back 1.000/0.094 1133.0,594.6  right 0.749/-0.037 1139.8,627.4
            set  shadow 1057.1,563.7  1067.7,627.6  1211.9,627.6  1195.6,563.7  dark 0.660  soft 3.1
            set  shade  diff 0.955  spec 0.101  rim 0.032  hot 0.233,0.263
            pile face   433.8,340.4  516.7,340.4  501.8,409.9  414.5,409.9
            pile solid  near 0.749/-0.037 459.1,416.4  front 1.000/0.094 466.9,374.2
            pile shadow 439.8,357.6  522.5,357.6  508.0,427.9  420.9,427.9  dark 0.545  soft 10.6
            pile shade  diff 0.955  spec 0.103  rim 0.046  hot 0.263,0.767
            """.trimIndent(),
            actual = dump(Steep),
        )
    }

    // ---- the recording is allowed to be stable ------------------------------------

    /**
     * The scene is only a recording if it records the same thing twice.
     *
     * Quantising is what buys that, and quantising is exactly what a value
     * sitting on a tie takes away again: at `x.x5` the rounding is decided by
     * the last bit of a `sin`, and the golden becomes a coin toss that lands the
     * same way for years and then flips on somebody's laptop. So the scene's
     * coordinates were chosen — and, where they had to be, nudged — until
     * nothing in any of the three dumps comes near one.
     */
    @Test
    fun noNumberInAnyGoldenSitsOnARoundingBoundary() {
        val onEdge = mutableListOf<String>()

        listOf("home" to Home, "turned" to Turned, "steep" to Steep).forEach { (seat, pose) ->
            val dump = StageDump(pose.planeFor(SURFACE_WIDTH, SURFACE_HEIGHT))
            dump.record(Scene)

            dump.quantised.forEach { scaled ->
                val toTie = abs(abs(scaled - scaled.toInt().toFloat()) - 0.5f)
                if (toTie <= TIE_MARGIN) onEdge += "$seat: $scaled"
            }
        }

        // Every offender at once rather than the first: fixing one is a nudge to
        // a scene coordinate, and a nudge moves every other number in the dump,
        // so finding them one failure at a time is a search that does not
        // converge in any pleasant number of runs.
        assertTrue(
            onEdge.isEmpty(),
            "these quantised values round on a knife edge: ${onEdge.joinToString("  ")}",
        )
    }

    private companion object {

        /**
         * The three poses, built the way the stage builds them.
         *
         * None of them is a round number by accident. The turn is thirty-eight
         * degrees rather than forty-five because at forty-five a square mat is
         * symmetric about the axis the camera looks down, and half the scene's
         * coordinates would coincide with the other half — which is a dump that
         * looks reassuringly tidy while testing less than it appears to. The
         * steep seat sits at fifty-two degrees, inside
         * [com.kaiharimoto.mastertool.core.layout.CameraEnvelope]'s ceiling, and
         * far enough back that its solved distance floor is not what is being
         * recorded here.
         */
        val Home = CameraPose()
        val Turned = CameraPose(yawDegrees = 38f, pitchDegrees = 15f, distance = 1.45f)
        val Steep = CameraPose(yawDegrees = 0f, pitchDegrees = 52f, distance = 1.9f)

        /** A landscape tablet's mat, which is the surface this app is judged on. */
        const val SURFACE_WIDTH = 1600f
        const val SURFACE_HEIGHT = 1000f

        /**
         * How close to a tie a quantised value is allowed to come.
         *
         * A five-hundredth of a quantum — two ten-thousandths of a pixel — and
         * the number is a compromise rather than a comfortable margin, so it is
         * worth being plain about both ends of it. The largest coordinate in
         * these dumps is around thirteen hundred pixels, where one last bit of a
         * `Float` is about a thousandth of a quantum; a `sin` that disagrees by
         * an ulp between the JVM and a Native target moves a value by roughly
         * that. So this is a few times the noise, not a hundred times it. It
         * cannot be raised much either: every number in every dump has to clear
         * it, and at ten times this the scene becomes a thing you place by
         * search rather than by choosing where the cards go.
         *
         * It is set where it is because it is enough to catch what actually
         * happens, which is not a coordinate landing near a tie by chance but a
         * constant landing *on* one exactly — see [CARD_HEIGHT], which is how
         * this test paid for itself on its first run.
         */
        const val TIE_MARGIN = 0.002f

        /**
         * Twenty-six cards in the graveyard, which is a mid-game board.
         *
         * Declared before the scene that reads it, because a companion's
         * properties initialise in the order they are written and a forward
         * reference here would silently hand the pile a depth of zero.
         */
        val GRAVEYARD_DEPTH = CardSolid.pileDepth(26, CARD_WIDTH)

        /**
         * The scene, at fixed coordinates, hand-built.
         *
         * Deliberately *not* routed through
         * [com.kaiharimoto.mastertool.core.layout.BoardLayouter]. The fitter is
         * supposed to be free to re-solve for any surface it is given, and
         * pinning its output inside a golden would turn every legitimate piece
         * of layout work into a failure in a file about lighting. What is
         * recorded here is what the renderer does with coordinates, so the
         * coordinates are constants.
         *
         * The four are chosen to cover the cases that behave differently rather
         * than to look like a board:
         *
         * - **rest**: flat on the felt, the case every other object is a
         *   departure from, and the one where the cast shadow should land back
         *   under the card that threw it. Foil — a face-up extra-deck monster —
         *   because square to the light is the only place a forty-four-power
         *   exponent leaves a highlight to record at all.
         * - **held**: tilted in the air on two axes, so its corners are at four
         *   different depths and its shadow is a sheared quad rather than an
         *   offset copy. Ordinary card stock, and the `spec 0.000` on its shade
         *   line is not a gap in the recording: it is the recorded fact that a
         *   card banked out of the mirror direction has no pool on it, which is
         *   the thing the line above it is there to be contrasted with. It
         *   floats ninety-two pixels up rather than a rounder ninety-six, and
         *   that is the second of this file's two nudges: at ninety-six, two of
         *   its numbers in the steep dump — the near edge of its solid and one
         *   corner of its shadow — came down within a whisker of a tie.
         * - **set**: face-down in defence, which is the one pose where the order
         *   the three Euler angles compose in is observable at all (see [Rot3]).
         * - **pile**: a graveyard with a real height, sitting on top of its own
         *   body the way [CardSolid.pileDepth] and the play stage arrange it.
         *
         * The positions are whole pixels, and none of the four sits on the mat's
         * own mid-row or shares a coordinate with another. Both of those are
         * about rounding rather than about looks. The projection is a scale that
         * is irrational nearly everywhere, which is what usually saves a clean
         * input from becoming a clean output — but along the row through the
         * vanishing point the scale is exactly one, so there the projection is
         * the identity and a tidy coordinate arrives as a tidy coordinate,
         * ties and all.
         */
        val Scene = listOf(
            StageObject(
                name = "rest",
                pose = Pose3(position = Vec3(430f, 660f, 0f)),
                material = CardMaterial.Foil,
                depth = CardSolid.thickness(CARD_WIDTH),
            ),
            StageObject(
                name = "held",
                pose = Pose3(
                    position = Vec3(910f, 380f, 92f),
                    rotX = -21f,
                    rotY = 27f,
                ),
                material = CardMaterial.Gloss,
                depth = CardSolid.thickness(CARD_WIDTH),
            ),
            StageObject(
                name = "set",
                pose = Pose3(
                    position = Vec3(1210f, 690f, 0f),
                    rotY = 180f,
                    rotZ = 90f,
                ),
                material = CardMaterial.Sleeve,
                depth = CardSolid.thickness(CARD_WIDTH),
            ),
            StageObject(
                name = "pile",
                // The z and the depth are one number twice, and that is the
                // arrangement rather than a coincidence: a pile's pose is the
                // top card, so the body it extrudes downward has to be exactly
                // the height it is standing at, or the deck floats or sinks.
                pose = Pose3(position = Vec3(330f, 240f, GRAVEYARD_DEPTH)),
                material = CardMaterial.Gloss,
                depth = GRAVEYARD_DEPTH,
            ),
        )

        fun dump(pose: CameraPose): String =
            StageDump(pose.planeFor(SURFACE_WIDTH, SURFACE_HEIGHT)).apply { record(Scene) }.text

        /**
         * The failure, made readable.
         *
         * `assertEquals` on two twenty-line blobs prints two twenty-line blobs,
         * and the next person to see one regenerates it without reading it —
         * which converts this test from a tripwire into a chore. So the first
         * line that differs is found and named, because the first token on every
         * line is the object and the second is the field, and then the whole new
         * dump is printed at the bottom: if the change was intended, re-recording
         * is a copy and a paste, and there is no reason to reach for a
         * regeneration flag that would also be a way to green the build without
         * looking.
         */
        fun assertGolden(seat: String, expected: String, actual: String) {
            if (expected == actual) return

            val want = expected.lines()
            val got = actual.lines()
            val at = (0 until maxOf(want.size, got.size))
                .first { want.getOrNull(it) != got.getOrNull(it) }

            fail(
                buildString {
                    appendLine("the $seat seat renders the scene differently than recorded.")
                    appendLine("first difference at line ${at + 1}:")
                    appendLine("  recorded: ${want.getOrNull(at) ?: "(nothing — the dump is longer)"}")
                    appendLine("  rendered: ${got.getOrNull(at) ?: "(nothing — the dump is shorter)"}")
                    appendLine()
                    appendLine("if that was the intention, the whole new dump is:")
                    appendLine(actual)
                },
            )
        }
    }
}

/**
 * A card, at very nearly the size the rest of the render tests use it.
 *
 * The height is the nudge the recording needed, and it is worth writing down
 * because it is the whole hazard in miniature. A hundred and seventy-five is the
 * obvious number and the one this cannot have: `Shadows` sets a resting card's
 * feathering at `height × 0.018`, which at 175 is exactly 3.15 — a value sitting
 * precisely on a rounding tie, decided by whichever way the last bit of that
 * constant happens to fall. [GoldenStageTest.noNumberInAnyGoldenSitsOnARoundingBoundary]
 * caught it on the first run. One pixel shorter is the same card, at the same
 * proportions to anyone looking, and is not on a boundary.
 */
private const val CARD_WIDTH = 120f
private const val CARD_HEIGHT = 174f

/** One thing on the stage: where it is, what it is made of, how deep its body is. */
private class StageObject(
    val name: String,
    val pose: Pose3,
    val material: CardMaterial,
    val depth: Float,
)

/**
 * The scene, rendered to text.
 *
 * Four lines per object, one field each, so a failure can point at a line and
 * name what moved:
 *
 * - `face` — the four corners of the printed face, projected to the screen.
 *   [StagePlane.project] rather than [StagePlane.flatten], because project *is*
 *   where the pixel lands: the mat's own layer applies the same transform to
 *   everything flatten hands it, so the two compose back to this.
 * - `solid` — the faces of the slab that survive back-face culling, each as
 *   `brightness/warmth` followed by where its centre projects. The centre is
 *   what carries a pile's height into the record: the normals of a deck's edges
 *   do not change when the pile curve does, but where the edge sits does. The
 *   warmth is here because the rig mixes three lamps of three temperatures, so
 *   which lamps reached a face is now as much a fact about the picture as how
 *   much of them did.
 * - `shadow` — every corner of the cast shadow, plus how dark and how soft it
 *   is, which are the two numbers that say "resting" or "held above".
 * - `shade` — what the light does to the printed face.
 */
private class StageDump(private val plane: StagePlane) {

    /**
     * Every number in the dump, scaled by its own quantum and *before* rounding.
     *
     * Kept so the recording can be held to the thing that makes it a recording
     * at all: no value near enough to a tie for a last-bit difference in a `sin`
     * to flip it.
     */
    val quantised = mutableListOf<Float>()

    private val lines = mutableListOf<String>()

    /**
     * The eye, taken from the plane rather than passed in beside it.
     *
     * The lights live in the mat's own frame, so where the camera is *in that
     * frame* is a function of both the pitch and the yaw, and reading both off
     * the plane is what stops this recording from being of a scene lit from one
     * angle and photographed from another. It is also why the `turned` golden
     * carries real information: its `face` lines move because the projection
     * turned, and its `solid` and `shade` lines move because the light did.
     */
    private val eye = StageRig.eye(plane.tiltDegrees, plane.yawDegrees)

    val text: String get() = lines.joinToString("\n")

    fun record(scene: List<StageObject>) = scene.forEach { record(it) }

    private fun record(subject: StageObject) {
        val pose = subject.pose
        val slab = CardSolid.slab(pose, CARD_WIDTH, CARD_HEIGHT, subject.depth)
        val shown = CardSolid.visible(slab, eye)

        row(
            subject.name,
            "face",
            CardSolid.face(pose, CARD_WIDTH, CARD_HEIGHT)
                .joinToString("  ") { at(plane.project(it)) },
        )

        row(
            subject.name,
            "solid",
            slab.withIndex()
                .filter { (_, face) -> face in shown }
                .joinToString("  ") { (index, face) ->
                    val lit = StageRig.face(face, eye)
                    "${SLAB_FACES[index]} ${unit(lit.amount)}/${unit(lit.warmth)} " +
                        at(plane.project(face.centre))
                },
        )

        val shadow = Shadows.cast(
            pose,
            CARD_WIDTH,
            CARD_HEIGHT,
            StageRig.Key,
            cardHeight = CARD_HEIGHT,
        )
        row(
            subject.name,
            "shadow",
            if (shadow == null) {
                "none"
            } else {
                shadow.corners.joinToString("  ") { at(plane.project(it)) } +
                    "  dark ${unit(shadow.alpha)}  soft ${px(shadow.spread)}"
            },
        )

        val shade = Shading.of(pose, subject.material, StageRig.Key, eye)
        row(
            subject.name,
            "shade",
            "diff ${unit(shade.diffuse)}  spec ${unit(shade.specular)}  " +
                "rim ${unit(shade.fresnel)}  hot ${unit(shade.hotspot.x)},${unit(shade.hotspot.y)}",
        )
    }

    private fun row(name: String, field: String, body: String) {
        lines += name.padEnd(5) + field.padEnd(7) + body
    }

    private fun at(point: Projected) = "${px(point.x)},${px(point.y)}"

    /** A screen position, to a tenth of a pixel. */
    private fun px(value: Float) = fixed(value, decimals = 1)

    /**
     * A quantity in 0..1, to a thousandth.
     *
     * Finer than the pixels on purpose, and by exactly the right amount: a whole
     * card is about a hundred pixels across, so a thousandth of a unit interval
     * *is* a tenth of a pixel wherever one of these lands on the screen — a
     * hotspot's position, a diffuse step. One decimal here would have recorded
     * nothing at all, because the entire range a specular term uses is 0..0.5.
     */
    private fun unit(value: Float) = fixed(value, decimals = 3)

    /**
     * A float, quantised with integers.
     *
     * The single most load-bearing thing in the file, and the reason it does not
     * go anywhere near `Float.toString`. That prints as many digits as it takes
     * to round-trip the bits, so a golden built on it records the arithmetic's
     * last two bits — which differ between a JVM and a Native `sin`, and which
     * every harmless re-association of an expression perturbs. The file would
     * then churn on changes that move nothing anyone can see, and a file that
     * churns is a file people stop reading.
     *
     * Rounding to a fixed grid with [roundToInt] is instead all three things at
     * once: the cross-platform determinism, the tolerance this test would
     * otherwise need an `assertClose` for, and the promise that a diff here is a
     * difference somebody could have seen on the tablet.
     */
    private fun fixed(value: Float, decimals: Int): String {
        var steps = 1
        repeat(decimals) { steps *= 10 }

        val scaled = value * steps
        quantised += scaled

        val rounded = scaled.roundToInt()
        val magnitude = abs(rounded)
        val sign = if (rounded < 0) "-" else ""
        return "$sign${magnitude / steps}.${(magnitude % steps).toString().padStart(decimals, '0')}"
    }

    private companion object {
        /** The order [CardSolid.slab] hands its six faces back in. */
        val SLAB_FACES = listOf("back", "far", "right", "near", "left", "front")
    }
}
