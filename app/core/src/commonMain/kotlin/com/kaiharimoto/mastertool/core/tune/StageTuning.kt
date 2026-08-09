package com.kaiharimoto.mastertool.core.tune

import com.kaiharimoto.mastertool.core.layout.CameraEnvelope
import com.kaiharimoto.mastertool.core.layout.CameraPose
import com.kaiharimoto.mastertool.core.layout.StagePlane
import com.kaiharimoto.mastertool.core.layout.StageSeat
import kotlinx.serialization.Serializable

/**
 * The numbers the play stage is tuned by, as a document that can be moved.
 *
 * ## Why this exists at all
 *
 * Every value in here used to be a `private const val` next to the code that
 * read it, which is the right place for a number nobody is arguing about. These
 * are numbers somebody *is* arguing about — the angle of the camera, the lean of
 * a hand, how far a card comes off the felt to be read — and the only honest way
 * to settle that argument is to move the slider on the device it will be judged
 * on. A constant cannot be moved on a tablet. This can.
 *
 * ## The three rules that make it safe
 *
 * **[DEFAULT] is what shipped, to the bit.** Not "close to" — the same numbers
 * the constants had, which `StageTuningTest` pins. That is what lets this land
 * in a release before the panel does, lets a document stored by an older build
 * read back sensibly, and makes an export a *diff* rather than a snapshot of
 * somebody's afternoon.
 *
 * **Nothing in here re-solves a layout.** Every field is read either at draw
 * time or by the seat solver, and both of those already re-run when the board
 * changes. The numbers that *would* re-solve — `Scenery.ROOM_ABOVE`, the gap
 * fractions, the whole room — are deliberately absent; [StageReference] carries
 * them as read-only instead. The reason is one line in `MatInput`: the mat is a
 * single `pointerInput(layout)`, so re-solving the layout tears down the gesture
 * arbiter's event stream mid-gesture. A slider that re-solves is a slider that
 * can kill the drag which is moving it.
 *
 * **Every field is clamped on the way in and on the way out**, against
 * [StageKnobs] — the same bounds the sliders offer, from the same place, so the
 * two cannot drift. [sanitised] runs on load and on save like the rest of
 * `UiPreferences`, because a non-finite float reaching `StagePlane`'s
 * trigonometry is a stage that is broken *after a restart*, with no cause
 * visible and no way back.
 *
 * ## What it deliberately is not
 *
 * It is not a scene graph and it is not a settings screen. It is a short list of
 * numbers with a defensible default each, and the panel that drives it is an
 * instrument rather than a feature — which is why none of it appears in
 * `ShortcutTable` or `MatGuide`.
 */
@Serializable
data class StageTuning(
    val camera: CameraTune = CameraTune(),
    val hand: HandTune = HandTune(),
    val cards: CardTune = CardTune(),
    val focus: FocusTune = FocusTune(),
) {
    /**
     * Every field forced inside the range its own slider offers.
     *
     * Driven by [StageKnobs] rather than written out again, which is the only
     * arrangement where a knob cannot be added to the panel and forgotten here.
     */
    fun sanitised(): StageTuning =
        StageKnobs.ALL.fold(this) { document, knob -> knob.set(document, knob.get(document)) }

    /** True when nothing has been moved, so the stage can skip every override. */
    val isDefault: Boolean get() = this == DEFAULT

    companion object {
        val DEFAULT = StageTuning()
    }
}

/**
 * Where the stage opens, and through what.
 *
 * This is **not** the live camera — the rig owns that, and orbiting the table
 * does not write here. It is the seat the stage opens at, which is what "make it
 * the default" means for a camera. The panel writes both: the rig, so you can
 * see it, and this, so it is still there tomorrow.
 */
@Serializable
data class CameraTune(
    val yawDegrees: Float = StageSeat.TABLE.pose.yawDegrees,
    val pitchDegrees: Float = StageSeat.TABLE.pose.pitchDegrees,
    val distance: Float = StageSeat.TABLE.pose.distance,
    /** See [CameraPose.lens]: a focal length, with the subject pinned. */
    val lens: Float = StageSeat.TABLE.pose.lens,
) {
    fun pose(): CameraPose = CameraPose(yawDegrees, pitchDegrees, distance, lens)

    companion object {
        fun of(pose: CameraPose) =
            CameraTune(pose.yawDegrees, pose.pitchDegrees, pose.distance, pose.lens)
    }
}

/**
 * How a card in the hand is held.
 *
 * Two of these are one mechanism wearing two names, and `handLiftOf`'s KDoc has
 * the reason: the lean must pivot on the card's bottom edge, and the lift that
 * achieves it is `(h/2)·sin θ` — trigonometry, not taste. So [leanDegrees] is
 * free and [liftFactor] is a **multiplier on the solved value, floored at one**.
 * Below the solved value the bottom corner of every hand card goes under the
 * felt, and `Shadows.cast` slides a corner that is already below the surface, so
 * half the shadow quad folds back through itself. That shipped once.
 */
@Serializable
data class HandTune(
    /** How far a hand card leans back. Negative leans away from the viewer. */
    val leanDegrees: Float = -24f,
    /** A multiplier on the lift the lean solves for. Never below one. */
    val liftFactor: Float = 1f,
    /** The gap between hand cards, as a fraction of a card's width. */
    val stepFraction: Float = 0.62f,
    /** How far a card raised to be read comes up, as a ratio of [CardTune.carryLift]. */
    val liftRatio: Float = 1.6f,
)

/** How far things come off the felt, and how big they get when they do. */
@Serializable
data class CardTune(
    /**
     * The master lift: how far a carried card floats, in card heights.
     *
     * What tells you a card is off the table is not how much bigger it got —
     * that is a few per cent at any sane camera distance — it is how far its
     * shadow has walked out from under it, and that is linear in this.
     */
    val carryLift: Float = 0.55f,
    /** A spread pile, as a ratio of [carryLift]: over the board, under a hand. */
    val fanLiftRatio: Float = 0.85f,
    /** A card held up to be read, in card heights. Further than one being slid. */
    val peekLift: Float = 1.35f,
    /** And how much bigger it gets while it is up there. */
    val peekScale: Float = 1.9f,
)

/**
 * Depth of field, as far as this renderer can honestly go.
 *
 * **It is not a blur and it does not become one by being turned up.** No blur
 * primitive is reachable here: `BlurEffect` is API 31 against a `minSdk` of 26
 * and degrades to a silent no-op below it, and a `renderEffect` per card is the
 * one shape of change `docs/PHOTOREAL.md` measured and called fatal — an
 * offscreen layer and a tile flush per card, per frame.
 *
 * It is also worth far less than it sounds. Measured on this board at
 * 1600x1000: the playing area's whole depth span is 55 mat pixels at the reading
 * seat and 425 at the seated one, so a physically-tuned circle of confusion
 * comes out at **zero pixels overhead and under three seated**, on a card 102
 * pixels wide. A render-pass architecture to move three pixels is not a trade.
 *
 * What defocus destroys first is *micro-contrast*, long before sharpness, and at
 * two or three pixels of blur that is the whole of the visible effect. So this
 * is a contrast falloff with depth: the far half of the board loses its bite,
 * whites come toward the felt, the frame lines around distant art go grey, and
 * the board reads as receding. Every edge stays razor sharp; there is no bokeh
 * and there will not be one without a layer.
 *
 * **Off by default**, because it is a look rather than a correction, and because
 * an instrument that changes the picture before you have touched it is an
 * instrument you cannot read.
 */
@Serializable
data class FocusTune(
    /**
     * Where the plane of focus sits across the board's visible depth: −1 at its
     * far edge, 0 in the middle, +1 at the near edge.
     *
     * Normalised rather than in pixels because that span triples between the
     * reading seat and the seated one, so a focus distance in pixels would mean
     * a different thing at every seat.
     */
    val depth: Float = 0f,
    /**
     * The aperture, as a label. Smaller is shallower.
     *
     * Exported *beside* the gradient it maps to, so that changing the mapping
     * later can never silently retune a saved preset.
     */
    val fNumber: Float = 8f,
    /**
     * How strong the falloff is at full defocus, as an alpha.
     *
     * Zero is off and is the default. Past about 0.2 the far cards stop being
     * readable, which is the thing this application exists to prevent.
     */
    val strength: Float = 0f,
)

/**
 * One number a person can move: where it lives, what it is called, and how far
 * it may go.
 *
 * Data rather than a screen, for the same reason `ShortcutTable` and `MatGuide`
 * are data. The panel renders this list, [StageTuning.sanitised] clamps against
 * this list, and the exporter names its fields off this list — so a knob cannot
 * be offered by a slider that the document will not accept, or accepted by a
 * document that the export forgets.
 */
class Knob(
    /** Which heading it appears under. */
    val group: String,
    /** Its JSON path, e.g. `camera.lens`. Also its stable id. */
    val path: String,
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
    /** Shown after the value: `°`, `x`, `cards`. Empty for a bare number. */
    val unit: String,
    /** One line on the panel saying what moving it does. */
    val note: String,
    val get: (StageTuning) -> Float,
    private val write: (StageTuning, Float) -> StageTuning,
) {
    /** Writes [value] in, clamped and made finite. Never throws and never stores NaN. */
    fun set(document: StageTuning, value: Float): StageTuning =
        write(document, if (value.isFinite()) value.coerceIn(min, max) else get(StageTuning.DEFAULT))

    /** Where in `min..max` a value sits, for a slider that speaks in fractions. */
    fun fractionOf(value: Float): Float =
        if (max <= min) 0f else ((value - min) / (max - min)).coerceIn(0f, 1f)

    /** The value at [fraction] of the way along, snapped to [step]. */
    fun valueAt(fraction: Float): Float {
        val raw = min + (max - min) * fraction.coerceIn(0f, 1f)
        if (step <= 0f) return raw
        return (kotlin.math.round(raw / step) * step).coerceIn(min, max)
    }
}

/**
 * Every knob the panel offers, in the order it offers them.
 *
 * Kept short on purpose. The play stage has something like two hundred numbers
 * in it and almost all of them are load-bearing for a named test; these are the
 * ones a person can look at a picture and have an opinion about.
 */
object StageKnobs {

    const val CAMERA = "Camera"
    const val FOCUS = "Focus"
    const val HAND = "Hand"
    const val CARDS = "Cards"

    val ALL: List<Knob> = listOf(
        Knob(
            CAMERA, "camera.yawDegrees", "Yaw", -180f, 180f, 1f, "°",
            "Round the table. Zero is square on.",
            { it.camera.yawDegrees }, { d, v -> d.copy(camera = d.camera.copy(yawDegrees = v)) },
        ),
        Knob(
            CAMERA, "camera.pitchDegrees", "Pitch", 4f, 58f, 0.5f, "°",
            "How far the table is laid back. Four is overhead, fifty-eight is the floor of legibility.",
            { it.camera.pitchDegrees }, { d, v -> d.copy(camera = d.camera.copy(pitchDegrees = v)) },
        ),
        Knob(
            CAMERA, "camera.distance", "Distance", 0.8f, 2.6f, 0.01f, "x",
            "How far back you sit, in stage heights. The envelope may hold you further out at a low pitch.",
            { it.camera.distance }, { d, v -> d.copy(camera = d.camera.copy(distance = v)) },
        ),
        Knob(
            CAMERA, "camera.lens", "Focal length", 0.7f, 2f, 0.02f, "x",
            "Wide to long, with the subject pinned. 0.7 is about 23mm, 2.0 about 65mm.",
            { it.camera.lens }, { d, v -> d.copy(camera = d.camera.copy(lens = v)) },
        ),

        Knob(
            FOCUS, "focus.strength", "Defocus", 0f, 0.25f, 0.005f, "",
            "A contrast falloff with depth, not a blur. Zero is off. Past 0.2 far cards stop being readable.",
            { it.focus.strength }, { d, v -> d.copy(focus = d.focus.copy(strength = v)) },
        ),
        Knob(
            FOCUS, "focus.depth", "Focus plane", -1f, 1f, 0.02f, "",
            "Where it is sharp, across the board's depth. Minus one is the far edge, plus one the near.",
            { it.focus.depth }, { d, v -> d.copy(focus = d.focus.copy(depth = v)) },
        ),
        Knob(
            FOCUS, "focus.fNumber", "Aperture", 2f, 22f, 0.5f, "f/",
            "How fast it falls away from the focus plane. Smaller is shallower.",
            { it.focus.fNumber }, { d, v -> d.copy(focus = d.focus.copy(fNumber = v)) },
        ),

        Knob(
            HAND, "hand.leanDegrees", "Lean", -45f, 0f, 1f, "°",
            "How far a hand card stands up off the felt. It pivots on its bottom edge.",
            { it.hand.leanDegrees }, { d, v -> d.copy(hand = d.hand.copy(leanDegrees = v)) },
        ),
        Knob(
            HAND, "hand.liftFactor", "Lift", 1f, 1.6f, 0.02f, "x",
            "A multiplier on the lift the lean solves for. One is exactly on the felt; below it the card sinks into the table.",
            { it.hand.liftFactor }, { d, v -> d.copy(hand = d.hand.copy(liftFactor = v)) },
        ),
        Knob(
            HAND, "hand.stepFraction", "Spacing", 0.3f, 1.1f, 0.01f, "cards",
            "The gap between hand cards, in card widths. Below about a half they overlap.",
            { it.hand.stepFraction }, { d, v -> d.copy(hand = d.hand.copy(stepFraction = v)) },
        ),
        Knob(
            HAND, "hand.liftRatio", "Read height", 0.6f, 2.6f, 0.02f, "x",
            "How far a card raised to be read comes up, against a carried one.",
            { it.hand.liftRatio }, { d, v -> d.copy(hand = d.hand.copy(liftRatio = v)) },
        ),

        Knob(
            CARDS, "cards.carryLift", "Carry height", 0.2f, 1.2f, 0.01f, "cards",
            "How far a card floats while it is being moved. Its shadow walks out linearly with this.",
            { it.cards.carryLift }, { d, v -> d.copy(cards = d.cards.copy(carryLift = v)) },
        ),
        Knob(
            CARDS, "cards.fanLiftRatio", "Spread height", 0.3f, 1.4f, 0.01f, "x",
            "How far a searched pile floats over the board, against a carried card.",
            { it.cards.fanLiftRatio }, { d, v -> d.copy(cards = d.cards.copy(fanLiftRatio = v)) },
        ),
        Knob(
            CARDS, "cards.peekLift", "Peek height", 0.6f, 2.4f, 0.02f, "cards",
            "How far a held card comes off the table to be read.",
            { it.cards.peekLift }, { d, v -> d.copy(cards = d.cards.copy(peekLift = v)) },
        ),
        Knob(
            CARDS, "cards.peekScale", "Peek size", 1f, 2.6f, 0.02f, "x",
            "And how much bigger it gets while it is up there.",
            { it.cards.peekScale }, { d, v -> d.copy(cards = d.cards.copy(peekScale = v)) },
        ),
    )

    /** The knobs of one group, in order. */
    fun of(group: String): List<Knob> = ALL.filter { it.group == group }

    val GROUPS: List<String> = listOf(CAMERA, FOCUS, HAND, CARDS)
}

/**
 * The numbers the panel shows and will not let you move.
 *
 * Exported so a paste-back is a complete picture rather than fifteen sliders out
 * of context. Everything here either re-solves a layout, is pinned by a named
 * test, or both — `docs/TUNING.md` says which. They are read straight off the
 * live constants rather than typed again, so this cannot drift from the build it
 * came out of; a reference table that lies is worse than none.
 */
@Serializable
data class StageReference(
    val envelope: EnvelopeReference = EnvelopeReference(),
    val seats: Map<String, CameraTune> =
        StageSeat.entries.associate { it.name to CameraTune.of(it.pose) },
    val tiltDefault: Float = StagePlane.TILT,
    val homeDistance: Float = CameraPose.HOME_DISTANCE,
    val safeDepth: Float = StagePlane.SAFE_DEPTH,
)

@Serializable
data class EnvelopeReference(
    val minPitch: Float = CameraEnvelope().minPitch,
    val maxPitch: Float = CameraEnvelope().maxPitch,
    val minDistance: Float = CameraEnvelope().minDistance,
    val maxDistance: Float = CameraEnvelope().maxDistance,
    val minLens: Float = CameraEnvelope().minLens,
    val maxLens: Float = CameraEnvelope().maxLens,
)
