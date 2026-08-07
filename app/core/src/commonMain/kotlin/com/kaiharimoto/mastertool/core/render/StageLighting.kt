package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.motion.Vec3

/**
 * The three lamps, as one value.
 *
 * [StageRig]'s KDoc says two rigs would be one too many, and that is still the
 * rule this exists to keep rather than to break. What it argues is that every
 * surface on the table has to agree about where the light is — a card's face,
 * the white edge of a pile, the felt, the shadow one card throws on another —
 * or the table stops reading as one room. The way it guaranteed that was by
 * being a singleton, which guarantees agreement and also guarantees there is
 * only ever one room.
 *
 * So the guarantee moves down a level: there is exactly one *value* in play at
 * any moment, threaded from the camera state through every draw, and it is not
 * possible for one surface to be shaded by one preset and its neighbour by
 * another because no call site holds a lamp of its own any more. A scene picks
 * a rig; the rig is then unanimous, which is the whole of what the original
 * rule was buying.
 *
 * The arithmetic did not have to move at all. [StageRig.lit] already took its
 * three lamps as parameters and only defaulted them, so this is a value being
 * passed where a default used to be read.
 *
 * ## Why the ambient never goes low
 *
 * The obvious way to make a night scene is to turn the ambient down, and it is
 * the one move this file will not make. [Tone.veil] carries the reason with
 * numbers: a card's own art is a picture the renderer cannot read, so it is
 * shaded by one black overlay at one opacity, and that approximation's error
 * grows sharply as the light falls — dark channels come back 1.19x too bright
 * at a diffuse of 0.72, 1.44x at 0.5, 1.87x at 0.3. Below about 0.5 the honest
 * fix is not a different constant but drawing a face's shading some other way
 * than one flat overlay, which is a piece of work and not a lighting preset.
 *
 * [NIGHT_FLOOR] is therefore a floor rather than a preference, and a test holds
 * every preset above it. Night gets its darkness the way the handbook already
 * wants it to: the *room* falls away, and the mat stays the brightest thing in
 * the frame, because a card you cannot read is a card this application has
 * failed.
 */
data class StageLighting(
    val key: Light,
    val bounce: Light,
    val rim: Light,
) {
    companion object {

        /**
         * The lowest ambient any preset may ask for. See the note above.
         *
         * Chosen at 0.55 rather than at the 0.5 where [Tone.veil]'s KDoc puts
         * the cliff, because the number quoted there is where the approximation
         * *starts* being wrong by more than the eye forgives on dark art, not
         * where it becomes fine.
         */
        const val NIGHT_FLOOR = 0.55f

        /**
         * The stage as it has always been: [StageRig]'s own three lamps.
         *
         * Deliberately a reference rather than a copy. Those three `val`s carry
         * the argument for every number in them, and `GoldenStageTest` is a
         * recording of what they produce — so the minimal scene has to be the
         * same objects, not the same numbers typed again somewhere they could
         * drift.
         */
        val Minimal = StageLighting(
            key = StageRig.Key,
            bounce = StageRig.Bounce,
            rim = StageRig.Rim,
        )

        /**
         * Daylight: a window high on the left wall, and a room that is bright.
         *
         * Three things separate it from a lamp, and none of them is "brighter".
         *
         * **It is cool, and the bounce it throws is warm.** Sunlight through
         * glass is the cool end of anything anybody has a bulb for, and what it
         * lands on in this scene is a wooden desk, which sends back the other
         * half of the spectrum. That split across one object is the read — see
         * [StageRig.Bounce] for why a difference *across* a surface carries
         * far further than the same shift applied to everything.
         *
         * **It is high-key.** The ambient is the highest of the three presets
         * because a room with a window in it genuinely is: light arrives from
         * the whole sky and every wall, not from one point, and the shadows are
         * correspondingly shallow. That leaves less headroom for the
         * directional terms, which is not a loss — it is what daylight looks
         * like, and the specular is still doing the talking.
         *
         * **It comes from the side rather than from over your shoulder.** The
         * key travels across the table and only then down it, so the long axis
         * of every shadow lies across the board instead of down it.
         */
        val DeskDay = StageLighting(
            key = Light(
                direction = Vec3(0.58f, 0.34f, -0.74f).normalised(),
                intensity = 1f,
                warmth = -0.55f,
                ambient = 0.76f,
            ),
            bounce = Light(
                direction = Vec3(-0.42f, -0.26f, -0.87f).normalised(),
                intensity = 0.34f,
                warmth = 0.85f,
                ambient = 0f,
            ),
            rim = Light(
                direction = Vec3(-0.58f, -0.70f, -0.42f).normalised(),
                intensity = 0.5f,
                warmth = -0.8f,
                ambient = 0f,
            ),
        )

        /**
         * Night: one lamp on the desk, to the right, and a room that is not lit.
         *
         * The warmest key in the app by a wide margin, and the only one that
         * approaches the full swing [Lit.TEMPERATURE] allows. A tungsten bulb
         * against daylight is genuinely that far over, and this is the one
         * scene where nothing else in frame is arguing for a different white.
         *
         * The ambient falls to [NIGHT_FLOOR] and stops there, which is the whole
         * discipline of this preset. What makes it read as night is not the mat
         * going dark — it is that the lamp is *on one side of the table*, the
         * bounce is nearly gone because there is no lit room to bounce off, and
         * everything past the desk has nothing lighting it at all. Contrast, not
         * absence.
         *
         * The key sits opposite the day's so that switching between the two is a
         * change of room rather than a change of colour: every shadow on the
         * board swings across to the other side, which is the one cue nobody
         * needs pointing out.
         */
        val DeskNight = StageLighting(
            key = Light(
                direction = Vec3(-0.54f, 0.36f, -0.76f).normalised(),
                intensity = 1f,
                warmth = 0.95f,
                ambient = NIGHT_FLOOR,
            ),
            bounce = Light(
                direction = Vec3(0.34f, -0.30f, -0.89f).normalised(),
                intensity = 0.20f,
                warmth = -0.85f,
                ambient = 0f,
            ),
            rim = Light(
                direction = Vec3(0.56f, -0.68f, -0.47f).normalised(),
                intensity = 0.38f,
                warmth = -0.45f,
                ambient = 0f,
            ),
        )
    }
}
