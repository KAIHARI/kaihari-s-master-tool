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
            rest face   362.1,569.4  484.3,569.4  469.8,745.6  342.0,745.6
            rest solid  right 0.817/-0.080 477.3,655.6  near 0.779/-0.059 406.0,745.7  front 1.000/0.098 414.8,655.5
            rest shadow 361.9,569.1  484.1,569.1  469.6,745.2  341.9,745.2  dark 0.660  soft 3.1
            rest shade  diff 0.955  spec 0.264  rim 0.001  hot 0.305,0.440
            held face   859.4,256.2  965.2,265.5  965.2,441.4  859.4,439.1
            held solid  near 0.788/-0.066 913.3,440.4  left 0.804/0.078 859.4,347.8  front 0.920/0.007 913.4,350.6
            held shadow 906.4,398.1  990.7,357.2  974.6,472.3  887.5,515.8  dark 0.352  soft 34.2
            held shade  diff 0.837  spec 0.000  rim 0.002  hot -0.382,-0.134
            set  face   1134.8,625.4  1145.3,748.8  1330.8,748.8  1314.5,625.4
            set  solid  back 1.000/0.098 1231.1,686.3  far 0.804/0.078 1139.9,686.2  right 0.779/-0.059 1238.0,748.8
            set  shadow 1134.5,625.1  1145.1,748.4  1330.5,748.4  1314.3,625.1  dark 0.660  soft 3.1
            set  shade  diff 0.955  spec 0.137  rim 0.000  hot 0.560,0.305
            pile face   297.7,176.7  411.4,176.7  394.9,323.5  276.3,323.5
            pile solid  right 0.817/-0.080 409.1,260.4  near 0.779/-0.059 342.5,334.5  front 1.000/0.098 345.3,248.6
            pile shadow 311.9,201.6  422.4,201.6  406.8,345.1  291.7,345.1  dark 0.660  soft 3.1
            pile shade  diff 0.955  spec 0.228  rim 0.001  hot 0.305,0.440
            deck face   1340.7,222.3  1224.8,222.3  1243.2,374.0  1364.1,374.0
            deck solid  back 1.000/0.098 1276.4,322.1  right 0.804/0.078 1226.4,309.5  near 0.779/-0.059 1294.9,385.9
            deck shadow 1322.9,249.8  1210.9,249.8  1228.0,397.4  1344.8,397.4  dark 0.660  soft 3.1
            deck shade  diff 0.955  spec 0.137  rim 0.000  hot 0.305,0.440
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
            rest solid  right 0.794/-0.065 645.5,824.5  near 0.798/-0.071 649.2,941.3  front 1.000/0.099 593.9,864.9
            rest shadow 487.8,830.1  591.0,750.9  700.6,899.7  596.1,983.2  dark 0.660  soft 3.1
            rest shade  diff 0.955  spec 0.089  rim 0.000  hot 0.207,0.343
            held face   721.5,266.8  798.9,211.9  898.8,356.9  828.0,418.1
            held solid  near 0.814/-0.083 864.3,386.8  left 0.804/0.078 775.3,343.3  front 0.923/0.004 813.2,313.4
            held shadow 816.7,353.6  855.8,271.3  917.4,375.6  878.5,461.3  dark 0.352  soft 34.2
            held shade  diff 0.837  spec 0.000  rim 0.009  hot -0.574,-0.330
            set  face   1129.6,407.9  1209.1,497.6  1335.9,396.2  1255.9,309.7
            set  solid  back 1.000/0.099 1232.7,402.2  far 0.804/0.078 1168.9,452.5  right 0.798/-0.071 1273.0,446.5
            set  shadow 1129.3,407.8  1208.7,497.5  1335.5,396.1  1255.5,309.6  dark 0.660  soft 3.1
            set  shade  diff 0.955  spec 0.107  rim 0.000  hot 0.657,0.207
            pile face   141.6,540.4  247.8,466.4  346.6,605.3  239.0,683.2
            pile solid  right 0.794/-0.065 304.8,540.7  near 0.798/-0.071 301.6,648.0  front 1.000/0.099 243.5,572.7
            pile shadow 162.7,551.5  265.3,479.8  361.3,614.5  257.4,689.9  dark 0.660  soft 3.1
            pile shade  diff 0.955  spec 0.120  rim 0.000  hot 0.207,0.343
            deck face   1048.3,-30.9  961.6,30.3  1069.7,147.9  1157.1,83.8
            deck solid  back 1.000/0.099 1050.1,84.9  right 0.804/0.078 1011.3,102.2  near 0.798/-0.071 1108.2,129.1
            deck shadow 1040.1,-0.5  956.2,59.0  1060.4,173.3  1145.0,111.1  dark 0.660  soft 3.1
            deck shade  diff 0.955  spec 0.107  rim 0.000  hot 0.207,0.343
            """.trimIndent(),
            actual = dump(Turned),
        )
    }

    @Test
    fun theSceneFromALowSteepSeatIsWhatItWasWhenThisWasRecorded() {
        assertGolden(
            seat = "steep",
            expected = """
            rest face   472.3,534.3  563.7,534.3  545.4,624.9  446.8,624.9
            rest solid  right 0.817/-0.080 554.9,578.1  near 0.749/-0.037 496.1,625.1  front 1.000/0.094 507.5,577.9
            rest shadow 472.1,534.1  563.6,534.1  545.2,624.7  446.7,624.7  dark 0.660  soft 3.1
            rest shade  diff 0.955  spec 0.069  rim 0.069  hot 0.263,0.767
            held face   840.6,331.3  914.7,354.8  919.9,457.6  842.5,435.5
            held solid  near 0.753/-0.041 881.7,446.9  left 0.804/0.078 841.5,382.4  front 0.925/0.003 879.8,393.8
            held shadow 877.4,451.1  937.8,432.0  928.6,486.5  864.9,507.7  dark 0.352  soft 34.2
            held shade  diff 0.837  spec 0.000  rim 0.014  hot -0.414,0.299
            set  face   1052.9,562.5  1066.5,626.6  1209.6,626.6  1188.7,562.5
            set  solid  back 1.000/0.094 1129.1,594.1  far 0.804/0.078 1059.5,593.9  right 0.749/-0.037 1138.0,626.8
            set  shadow 1052.7,562.3  1066.3,626.4  1209.3,626.4  1188.5,562.3  dark 0.660  soft 3.1
            set  shade  diff 0.955  spec 0.101  rim 0.032  hot 0.233,0.263
            pile face   451.3,334.7  530.3,334.7  512.4,398.9  428.2,398.9
            pile solid  right 0.817/-0.080 523.5,379.4  near 0.749/-0.037 472.6,412.7  front 1.000/0.094 480.9,365.8
            pile shadow 455.9,361.3  533.8,361.3  516.4,426.3  433.4,426.3  dark 0.660  soft 3.1
            pile shade  diff 0.955  spec 0.103  rim 0.046  hot 0.263,0.767
            deck face   1177.2,351.9  1096.4,351.9  1116.6,418.9  1202.9,418.9
            deck solid  back 1.000/0.094 1142.4,415.7  right 0.804/0.078 1103.7,400.1  near 0.749/-0.037 1156.8,435.0
            deck shadow 1171.4,382.8  1091.8,382.8  1111.4,450.8  1196.3,450.8  dark 0.660  soft 3.1
            deck shade  diff 0.955  spec 0.101  rim 0.032  hot 0.263,0.767
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
         *
         * ## The one time a recording had to be re-taken
         *
         * The focal length used to carry [CameraPose.distance] as well as the
         * lens, so walking toward the table quietly changed the field of view.
         * Taking it off moved every pose that is not at
         * [CameraPose.HOME_DISTANCE] — which is exactly one of these three.
         * [Home] and [Turned] are both at 1.45 and came out **byte for byte**
         * what they were, which is the property worth stating out loud: the
         * projection was corrected, and the two recordings that could not tell
         * did not move a digit. `CameraLensTest.atTheSeatEverythingWasTunedAtTheOldProjectionIsTheNewOne`
         * is that claim as arithmetic rather than as a diff.
         *
         * [Steep] moved, because it is meant to. It also moved from 1.9 to 1.96,
         * and that is the tie rule below rather than taste: at 1.9 the corrected
         * projection put a quantised value exactly on a rounding boundary, which
         * is a recording that lands the same way for years and then flips on
         * somebody's laptop. 1.96 clears [TIE_MARGIN] by twelve times, and the
         * distances either side of it were measured rather than guessed at.
         */
        val Home = CameraPose()
        val Turned = CameraPose(yawDegrees = 38f, pitchDegrees = 15f, distance = 1.45f)
        val Steep = CameraPose(yawDegrees = 0f, pitchDegrees = 52f, distance = 1.96f)

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
         * A forty-card deck, face down — the object this recording was missing.
         *
         * Its absence is not a gap in coverage so much as the reason a bug
         * shipped twice. Every object in the scene was face up, so every one of
         * them agreed with a body extruded along the card's own axis and one
         * extruded toward the table; the two only differ once something is
         * turned over. The deck and the extra deck are the *only* face-down
         * piles on the stage, they built their bodies upward into the air, and
         * nothing here or in `CardSolidTest` was looking.
         */
        val DECK_DEPTH = CardSolid.pileDepth(40, CARD_WIDTH)

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
                    // 1211 rather than 1210, and that is this file's third
                    // nudge. Teaching the recording to pass `bodyDepth` moved
                    // every shadow by the depth of its own body, and this one
                    // landed at 1195.3498 in the steep dump — half a hair off a
                    // rounding tie, where the last bit of a `sin` decides which
                    // way it goes.
                    position = Vec3(1211f, 690f, 0f),
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
            StageObject(
                // The deck: the same arrangement as `pile`, turned over. Its z
                // and its depth are one number twice for the same reason, and
                // the pair of them is the whole point — a face-down pile has to
                // reach the felt exactly as a face-up one does, and until it did
                // this object's `solid` line would have shown a body standing in
                // the air above its own card.
                name = "deck",
                pose = Pose3(position = Vec3(1300f, 300f, DECK_DEPTH), rotY = 180f),
                material = CardMaterial.Sleeve,
                depth = DECK_DEPTH,
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
        val shown = CardSolid.visible(slab, plane.eyePoint(eye))

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
            // The way the stage casts it. Left off, this recorded a pile
            // shadowing from its own top card — the very thing `bodyDepth` was
            // added to stop — so the recording went on agreeing with itself
            // while the shipped build threw the deck's shadow twice as far as
            // the bug it was meant to fix.
            bodyDepth = subject.depth,
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
