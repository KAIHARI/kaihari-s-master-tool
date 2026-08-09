package com.kaiharimoto.mastertool.core.render

import kotlin.math.abs

/**
 * How much bite a card loses for being far away.
 *
 * ## It is not depth of field and it is not pretending to be
 *
 * There is no blur on this stage and there is not going to be one:
 * `BlurEffect` is API 31 against a `minSdk` of 26 and degrades to a silent
 * no-op below it, and a `renderEffect` per card is the one shape of change
 * `docs/PHOTOREAL.md` measured and named fatal.
 *
 * A blur would also buy less than it costs. The board's whole depth span is 55
 * mat pixels at the reading seat and 425 at the seated one, so a
 * physically-tuned circle of confusion is **zero pixels overhead and under
 * three seated**, on a card 102 pixels wide.
 *
 * What defocus destroys first is micro-contrast, long before sharpness, and at
 * three pixels of blur that is the whole of the visible effect. So this returns
 * a *contrast* falloff, applied as one more rounded rectangle over a card the
 * renderer already draws two or three of. Zero layers, zero render passes, and
 * it reaches API 26.
 *
 * ## Depth is measured in the projection's own units
 *
 * Not in pixels of board, which would need the layout and would mean a
 * different thing at every seat. `StagePlane.project` reports a depth in the
 * same units as its `cameraDistance`, and the ratio between them is the only
 * scale-free thing available at a card's draw site. So the focus plane is a
 * fraction of the camera distance and the falloff is per unit of it — which
 * makes both numbers mean the same thing on a phone and on a desk monitor,
 * which is the same property `CameraPose.distance` has.
 *
 * ## What it will and will not look like
 *
 * The far half of the board loses its bite: whites come toward the felt, the
 * black frame lines around distant art go grey, and the board reads as
 * receding. Every edge stays razor sharp. There is no feathering of a near card
 * over what is behind it and there is no bokeh, because both of those need a
 * layer, which is the thing this exists to avoid.
 */
object Defocus {

    /**
     * How far from the focus plane a card may be before anything happens, as a
     * fraction of the camera distance.
     *
     * A dead zone rather than a curve through zero, because the alternative is
     * every card on the table being fractionally hazed — which is not a
     * shallow depth of field, it is a dirty screen.
     */
    const val GATE = 0.012f

    /**
     * The falloff at the reference aperture, per unit of camera distance.
     *
     * Chosen so that at f/8 — the default, and a deep stop — a card at the far
     * edge of the board at the seated seat gives up about a fifth of the
     * strength on the dial. That is a setting you have to look for. At f/2 it
     * saturates across the same span, which is the point of having the dial.
     */
    const val GRADIENT = 3.2f

    /** The aperture [GRADIENT] is quoted at. */
    const val REFERENCE_F = 8f

    /**
     * The haze over a card, as an alpha in 0..1.
     *
     * @param depth the card's depth, from `StagePlane.project`.
     * @param cameraDistance the plane's own, which is what [depth] is measured
     *   against. Zero or less means there is no camera yet — the first
     *   composition of the play screen builds exactly such a plane — and the
     *   honest answer there is none rather than a division.
     * @param focus where the sharp plane sits: −1 at the far end of what the
     *   stage can hold, 0 at the middle of the mat, +1 at the near end.
     * @param fNumber the aperture. Smaller is shallower.
     * @param strength the alpha at full defocus. Zero switches the whole thing
     *   off, and is the default everywhere.
     */
    fun hazeAt(
        depth: Float,
        cameraDistance: Float,
        focus: Float,
        fNumber: Float,
        strength: Float,
    ): Float {
        if (strength <= 0f || cameraDistance <= 0f) return 0f
        if (!depth.isFinite() || !focus.isFinite() || !strength.isFinite()) return 0f

        // Both in fractions of the camera distance, which is the one scale-free
        // pair of numbers available where a card is drawn.
        val at = depth / cameraDistance
        val plane = focus.coerceIn(-1f, 1f) * PLANE_REACH
        val away = abs(at - plane)
        if (away <= GATE) return 0f

        val stop = if (fNumber.isFinite() && fNumber > 0.1f) fNumber else REFERENCE_F
        val gradient = GRADIENT * (REFERENCE_F / stop)
        return ((away - GATE) * gradient).coerceIn(0f, 1f) * strength.coerceIn(0f, 1f)
    }

    /**
     * How far either way the focus plane may be put, as a fraction of the
     * camera distance.
     *
     * `StagePlane.SAFE_DEPTH` is where the projection stops being one at all, so
     * a focus plane is allowed anywhere the stage is allowed to have geometry
     * and nowhere else. Going further would give the dial a range in which
     * nothing changes, which reads as the dial being broken.
     */
    private const val PLANE_REACH = 0.5f
}
