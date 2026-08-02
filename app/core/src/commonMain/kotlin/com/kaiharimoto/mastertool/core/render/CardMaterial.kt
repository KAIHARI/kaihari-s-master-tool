package com.kaiharimoto.mastertool.core.render

import com.kaiharimoto.mastertool.core.model.Card

/**
 * What a card is made of, as far as light is concerned.
 *
 * Three finishes, because a real collection has three. Ordinary cards are
 * glossy card stock in a glossy sleeve; extra-deck cards are the metallic
 * frames, and in every list anyone actually plays they are the foiled ones;
 * the back of a sleeved card is the one surface that is not trying to catch
 * the light at all.
 */
enum class CardFinish { MATTE, GLOSS, FOIL }

/**
 * A surface's response to light.
 *
 * Blinn-Phong, which is the cheapest model that still gets the one thing a
 * card does right: the highlight is a *pool that moves*, not a brightness that
 * changes. A card you tilt to read the small text is doing that because the
 * pool is in the way, and no amount of "gets brighter when tilted" reproduces
 * it.
 */
data class CardMaterial(
    val finish: CardFinish,
    /** The Blinn-Phong exponent: higher is a tighter, harder highlight. */
    val shininess: Float,
    /** How bright the highlight gets at its best angle, 0..1. */
    val specular: Float,
    /**
     * How far the highlight splits into the prismatic ramp, 0..1.
     *
     * This is the app's own colour rule, kept: colour on a card face is
     * *light*, never decoration, so it appears only inside the specular term
     * and vanishes with it.
     */
    val iridescence: Float,
    /** How much the grazing-angle rim lights up, 0..1. */
    val rim: Float,
) {
    companion object {
        /** Sleeved card stock: the default, and most of the table. */
        val Gloss = CardMaterial(
            finish = CardFinish.GLOSS,
            shininess = 26f,
            specular = 0.34f,
            iridescence = 0f,
            rim = 0.5f,
        )

        /** A metallic frame: the extra deck, and it should look like it. */
        val Foil = CardMaterial(
            finish = CardFinish.FOIL,
            shininess = 44f,
            specular = 0.52f,
            iridescence = 0.8f,
            rim = 0.75f,
        )

        /** The back of a sleeve, which is the quietest surface on the table. */
        val Sleeve = CardMaterial(
            finish = CardFinish.MATTE,
            shininess = 10f,
            specular = 0.16f,
            iridescence = 0f,
            rim = 0.35f,
        )
    }
}

/** Which stock a given card is printed on. */
object CardStock {

    /**
     * The rule, in one place: you are looking at a sleeve back, a metallic
     * frame, or ordinary card stock.
     *
     * Deliberately derived from the card rather than assigned at random. A foil
     * that lands on a different card every time you reopen the table is a
     * particle effect wearing a material's clothes.
     */
    fun of(card: Card?, faceUp: Boolean): CardMaterial = when {
        !faceUp -> CardMaterial.Sleeve
        card != null && card.isExtraDeck -> CardMaterial.Foil
        else -> CardMaterial.Gloss
    }
}
