package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import coil3.compose.AsyncImage
import com.kaiharimoto.mastertool.core.board.BoardCard
import com.kaiharimoto.mastertool.core.board.CardPosition
import com.kaiharimoto.mastertool.core.board.DragOrigin
import com.kaiharimoto.mastertool.core.board.DropIntent
import com.kaiharimoto.mastertool.core.board.MatPoint
import com.kaiharimoto.mastertool.core.board.PlacedCard
import com.kaiharimoto.mastertool.core.board.PlayField
import com.kaiharimoto.mastertool.core.board.toPixels
import com.kaiharimoto.mastertool.core.layout.BoardLayout
import com.kaiharimoto.mastertool.core.layout.BoardLayouter
import com.kaiharimoto.mastertool.core.layout.BoardSlot
import com.kaiharimoto.mastertool.core.layout.Slot
import com.kaiharimoto.mastertool.core.layout.CameraRig
import com.kaiharimoto.mastertool.core.layout.StageSeat
import com.kaiharimoto.mastertool.core.layout.planeFor
import com.kaiharimoto.mastertool.core.mat.MatGestureMachine
import com.kaiharimoto.mastertool.core.model.Card
import com.kaiharimoto.mastertool.core.motion.Pose3
import com.kaiharimoto.mastertool.core.motion.SpringSpec
import com.kaiharimoto.mastertool.core.motion.Vec3
import com.kaiharimoto.mastertool.core.haptics.Haptic
import com.kaiharimoto.mastertool.core.render.CardSolid
import com.kaiharimoto.mastertool.core.render.CardStock
import com.kaiharimoto.mastertool.core.render.Rot3
import com.kaiharimoto.mastertool.ui.components.CARD_ASPECT_RATIO
import com.kaiharimoto.mastertool.ui.components.CardBack
import com.kaiharimoto.mastertool.ui.components.LocalCardBack
import com.kaiharimoto.mastertool.ui.deckbuilder.DeckBuilderState
import com.kaiharimoto.mastertool.core.input.ShortcutAction
import com.kaiharimoto.mastertool.core.input.ShortcutContext
import com.kaiharimoto.mastertool.core.input.ShortcutLayer
import com.kaiharimoto.mastertool.ui.fx.LocalFeedback
import com.kaiharimoto.mastertool.ui.fx.SoundEffect
import com.kaiharimoto.mastertool.ui.input.ShortcutHost
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

/**
 * How far a carried card rises off the mat, as a share of its own height.
 *
 * Raised along with the tilt and the lens. What tells you a card is off the
 * table is not how much bigger it got — that is a few per cent at any sane
 * camera distance — it is how far its shadow has walked out from under it, and
 * that is linear in this number.
 */
private const val LIFT_Z = 0.55f

/** A card raised to be *read* comes closer than one being slid. */
private const val HAND_LIFT = LIFT_Z * 1.6f

/**
 * How far a hand card leans back, so hand and table read as two objects.
 *
 * A hand that is *standing up* off the table is the difference between five
 * cards laid out on a mat and five cards being held, and it is the cheapest
 * three-dimensionality on the whole stage.
 *
 * But **it has to pivot on the card's bottom edge, not its centre**, and getting
 * that wrong shipped. A rotation about the centre keeps the centre where it is
 * and swings the *near edge down*: at twenty-four degrees that put the bottom of
 * every hand card a fifth of a card height underneath the felt. Two things then
 * go wrong at once, and both of them are visible from across a room. The card is
 * buried in the table — which nobody could see while the mat was black, and
 * everybody could see the moment the mat became a surface. And its shadow turns
 * inside out: `Shadows.cast` slides each corner along the light until it reaches
 * the table, and for a corner already *below* the table that distance is
 * negative, so half the shadow quad folds back through itself.
 *
 * The third thing is subtler and is the reason the fix is a fix rather than a
 * patch. A card leaned about its centre does not look tilted. It looks
 * *compressed* — foreshortened by nine per cent and otherwise unchanged, because
 * nothing about it has moved relative to the surface it is on. Pivot it on the
 * bottom edge instead and the bottom stays welded to the felt while the top
 * lifts a third of a card height into the air, its shadow lies down behind it,
 * and it reads as a card standing in a hand. See [HAND_LIFT_OF].
 */
private const val HAND_LEAN = -24f

/**
 * The lift that turns [HAND_LEAN] from a rotation about the centre into a
 * rotation about the bottom edge.
 *
 * Pure trigonometry rather than a number somebody liked: raising the centre by
 * `(h/2)·sin θ` puts the near edge back on the felt exactly, for any lean and
 * any card size.
 *
 * The hand's hit target is its [handPointFor] centre at z = 0, so the drawn card
 * now sits a little higher on the glass than the box that grabs it. That is
 * deliberate and safe here: the hand is a horizontal fan, cards are chosen by
 * which one your finger is *across*, and the vertical offset is a fraction of a
 * card that still lies well inside its own footprint. It would not be safe on
 * the mat, where cards are anywhere.
 */
private fun handLiftOf(cardHeight: Float, bodyDepth: Float): Float {
    val lean = abs(HAND_LEAN) * (PI.toFloat() / 180f)
    // The face's own half-height, plus the body hanging behind it — a card is a
    // slab and it is the *lowest point of the slab* that has to clear the felt,
    // not the lowest point of the printed side. Leaving the second term out
    // leaves one corner of the body a few pixels under the table, which is the
    // whole bug again in miniature and would fold one corner of the shadow.
    return cardHeight / 2f * sin(lean) + bodyDepth * cos(lean)
}

/**
 * A peeked card comes off the table and turns to face the reader.
 *
 * Further than a carried card, because this one is being *read* rather than
 * moved, and it reveals its face even if it is set — which is the whole
 * question a held finger is asking. Nothing about the field changes, so it
 * goes back down exactly as it was.
 */
private const val PEEK_Z = 1.35f
private const val PEEK_SCALE = 1.9f

private val TOP_BAR = 44.dp

/** Long enough to read "To the graveyard", short enough not to become scenery. */
private const val ANNOUNCEMENT_MILLIS = 1_600L

/**
 * The play stage: a deck, a table, and nothing telling you what you may do.
 *
 * This is the fishbowl grown up. The goldfish screen dealt cards onto a
 * perspective surface and let you look at them; here you pick them up, put them
 * anywhere, turn them over, stack them, and sweep them into the graveyard —
 * with the rules of the game living entirely in your head, which is what a
 * table is.
 *
 * **One plane, and one invariant that removes the need to sort anything: draw
 * order is depth.** Felt, then what is resting on it, then the shadows of what
 * is in the air, then what is in the air. There is no per-card depth sort
 * anywhere and there does not need to be.
 *
 * There used to be a second layer above this one, flat, holding whatever a hand
 * was carrying. It worked because there was only ever one camera angle, so
 * "flat" and "square to the reader" were the same thing and a card in the air
 * could live in the screen's frame while the felt lived in the mat's. A camera
 * that turns ends that — a carried card set in defence would stay square to the
 * glass while the table turned under it, and its own shadow would slide out
 * from beneath it. So there is one frame now. Height is carried by
 * `StagePlane.flatten`, which rewrites a point *with* a height as the point on
 * the felt that will look like it once the camera has run; that is what puts
 * the top card of a deck on top of the deck, and what keeps the card in your
 * hand turning with the table it left.
 *
 * Motion is one `withFrameNanos` loop over plain lists, with each card's pose
 * read inside its own `graphicsLayer`. Poses live in [StageCard] objects held by
 * the screen rather than remembered in composables, because a card picked up
 * changes parent — mat to air — which destroys and recreates its composable,
 * and a pose that reset at the moment of pickup would be a pose that reset on
 * the one frame it must not.
 *
 * **Everything with a height is real, and none of it is a 3D engine.** Cards
 * are solids in `core/render`: they have a thickness, a normal, an orientation
 * you can ask questions of, a light they respond to and a shadow they throw by
 * having each of their corners projected onto the felt. All of that arithmetic
 * lives in core where it is tested, and it reaches the screen through the one
 * thing Compose does give you for free — a `graphicsLayer` is a real
 * perspective-correct textured quad — plus a canvas for the geometry a layer
 * cannot hold. `StagePlane.flatten` is the join: it turns a point with a height
 * into the point on the mat that will look like it once the plane's own
 * transform has run, so shadows, pile edges and card thickness can all be drawn
 * by a canvas that only speaks two dimensions.
 */
@Composable
fun PlayScreen(state: DeckBuilderState, onBack: () -> Unit) {
    val deck = state.deck
    // A fresh shuffle each time the table is opened. The seed defaults to a
    // constant so tests can ask for a known deal; a demo that dealt the same
    // five cards to every person you showed it to would look broken.
    val play = remember(deck.main, deck.extra) {
        PlayState(deck.main, deck.extra, Random.nextLong())
    }
    val cards = remember(deck.main, deck.extra) { mutableMapOf<Int, StageCard>() }
    val machine = remember(deck.main, deck.extra) { MatGestureMachine() }
    // Above the shortcut host, because three of the shortcuts are seats. It
    // needs no surface to exist — `sync` tells it one as soon as there is a box.
    val camera = remember { StageCameraState(CameraRig(seat = StageSeat.TABLE)) }
    val feedback = LocalFeedback.current
    val back = LocalCardBack.current
    var menuFor by remember { mutableStateOf<DragOrigin?>(null) }
    var guide by remember { mutableStateOf(false) }

    // The pointer half of the standing rule that every gesture ships with both
    // idioms. Only the actions that need no card selected are here: a keyboard
    // has no way to say "this one" on a table where cards are anywhere rather
    // than in numbered zones, so anything about a particular card stays on the
    // card — touched, or through its menu.
    ShortcutHost(
        context = ShortcutContext(topLayer = ShortcutLayer.PLAY),
        onAction = { action ->
            when (action) {
                ShortcutAction.PLAY_DRAW ->
                    if (play.move { it.draw() }) feedback.play(SoundEffect.DEAL, Haptic.DEAL)
                ShortcutAction.PLAY_SHUFFLE ->
                    if (play.move { it.shuffleDeck(it.turn * 31L + it.deck.size) }) {
                        feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
                    }
                ShortcutAction.PLAY_NEW_HAND -> play.restart()
                ShortcutAction.PLAY_NEXT_PHASE -> play.move { it.nextPhase() }
                ShortcutAction.PLAY_END_TURN -> play.move { it.endTurn() }
                ShortcutAction.UNDO -> play.undo()
                ShortcutAction.REDO -> play.redo()
                ShortcutAction.PLAY_SEAT_OVERHEAD -> camera.rig.aimAt(StageSeat.OVERHEAD)
                ShortcutAction.PLAY_SEAT_TABLE -> camera.rig.aimAt(StageSeat.TABLE)
                ShortcutAction.PLAY_SEAT_SEATED -> camera.rig.aimAt(StageSeat.SEATED)
                ShortcutAction.PLAY_GUIDE -> guide = !guide
                // Outward one layer at a time, and the guide is the outermost
                // thing over the table: Esc with it open should close it, not
                // walk out of the stage and lose the board.
                ShortcutAction.DISMISS -> when {
                    guide -> guide = false
                    menuFor != null -> menuFor = null
                    else -> onBack()
                }
                else -> Unit
            }
        },
    ) {
    Box(Modifier.fillMaxSize().background(MasterToolPalette.Ink)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = TOP_BAR)) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            SideEffect { camera.sync(widthPx, heightPx) }

            val layout = remember(widthPx, heightPx) {
                BoardLayouter.solve(
                    width = widthPx,
                    height = heightPx,
                    aspectRatio = CARD_ASPECT_RATIO,
                    // The seat the stage opens at, and deliberately not the live
                    // camera. Handing the fitter a growth that changes every
                    // frame re-solves the whole board every frame: every card
                    // resizes, all sixty seats re-target, and `fits` can flip
                    // below MIN_CARD_WIDTH mid-gesture and unmount the stage.
                    // Handing it the *worst* growth the camera could ever reach
                    // is no better — that is the mat's diagonal, and it would
                    // cost a fifth of every card forever to buy an angle that
                    // might never be used. So the layout is solved once and the
                    // camera obeys it: CameraFit dollies back instead.
                    perspectiveGrowth = StageSeat.TABLE.pose
                        .planeFor(widthPx, heightPx)
                        .perspectiveGrowth,
                )
            }

            if (!layout.fits) {
                Text(
                    "Not enough room to lay a table out here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                return@BoxWithConstraints
            }

            val seats = remember(play.field, layout, play.carry, play.peeking) {
                // The camera is read as a plain field, not as observable state:
                // this must not re-run sixty times a second while the table is
                // turning. The cost is that a card already held up does not
                // re-face if the camera moves under it, which is a moment when
                // nobody is moving the camera.
                seatsFor(
                    field = play.field,
                    layout = layout,
                    carry = play.carry,
                    peeking = play.peeking,
                    facingReader = Rot3.facingViewer(
                        camera.pose.pitchDegrees,
                        camera.pose.yawDegrees,
                    ),
                )
            }

            // What the last release did, said once and then withdrawn. Left up,
            // it stops being news and starts being furniture that lies about
            // what just happened.
            play.announcement?.let { said ->
                LaunchedEffect(said) {
                    delay(ANNOUNCEMENT_MILLIS)
                    if (play.announcement == said) play.announcement = null
                }
            }

            // Both clocks report here, so a gesture the frame loop decides acts
            // on the same card the press landed on.
            val pilot = remember(play, machine, feedback) {
                MatPilot(machine, play, feedback, layout)
            }
            SideEffect {
                pilot.layout = layout
                // Asked before it opens rather than discovered when it draws.
                // A `DropdownMenu` with nothing in it is not nothing — it is a
                // three-pixel box that appears, sits there, and has to be
                // dismissed, which is a worse answer than the gesture having
                // done nothing at all.
                pilot.onMenu = { if (hasMenu(it)) menuFor = it }
                pilot.camera = camera
            }

            // Where a card the stage has never seen before comes from. Placing
            // it at its destination instead means a drawn card materialises in
            // the hand already there, which reads as a redraw of the screen
            // rather than as a card being dealt — and the opening five, all new
            // at once, are the first thing anyone sees.
            val dealFrom = remember(layout) {
                val deck = layout[BoardSlot.Deck]
                Pose3(
                    position = Vec3(
                        deck?.centerX ?: layout.field.centerX,
                        deck?.centerY ?: layout.field.centerY,
                        0f,
                    ),
                    rotY = 180f,
                )
            }

            // Aim every card at where it now belongs. Done outside the frame
            // loop because it only changes when the board does.
            seats.forEach { seat ->
                val card = cards.getOrPut(seat.id) { StageCard(seat.id).also { it.placeAt(dealFrom) } }
                card.pinned = seat.pinned
                card.cardWidth = seat.width
                card.aimAt(seat.pose)
            }

            // ---- the one loop -------------------------------------------------
            LaunchedEffect(Unit) {
                var previous = 0L
                while (true) {
                    withFrameNanos { now ->
                        val dt = if (previous == 0L) 0f else (now - previous) / 1_000_000_000f
                        previous = now
                        // A finger held perfectly still produces no pointer
                        // events, so the long press has to be driven from here.
                        pilot.tick(now / 1_000_000L)
                        val step = dt.coerceAtMost(0.05f)
                        cards.values.forEach { it.step(SpringSpec.Bouncy, step) }
                        // Snappy rather than Bouncy: a card overshooting reads as
                        // weight, and a whole table overshooting reads as a lurch.
                        if (camera.rig.step(SpringSpec.Snappy, step)) {
                            camera.sync(widthPx, heightPx)
                        }
                    }
                }
            }

            // ---- one plane, and everything on it ---------------------------------
            //
            // There used to be a second layer above this one, flat, holding
            // whatever a hand was carrying, with its position projected by hand
            // so it left the plane without a seam. It worked because there was
            // only ever one camera angle: "flat" and "square to the reader" were
            // the same thing, so a card in the air could be drawn in the screen's
            // frame and a card on the felt in the mat's, and nobody had to say
            // which was which.
            //
            // A camera that turns ends that. In two frames a carried card set in
            // defence stays square to the glass while every card under it turns
            // with the table, and its own shadow — drawn in here, because a
            // shadow is on the table even when the thing casting it is not —
            // slides out from under it. So the second frame is gone: everything
            // is in the mat's, `flatten` carries the height, and the card in
            // your hand is turned by the same camera as the felt it left.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // The projection, said the other way round. `StagePlane`
                        // spins the mat about its own normal and then lays it
                        // back; Compose builds `Rx · Ry · Rz`, which turns a
                        // point about Z first and about X last — so these three
                        // lines *are* that projection, and the agreement is
                        // structural rather than a coincidence to maintain.
                        val plane = camera.plane
                        rotationZ = -plane.yawDegrees
                        rotationX = plane.tiltDegrees
                        scaleX = plane.zoom
                        scaleY = plane.zoom
                        cameraDistance = plane.cameraDistance / this.density
                    },
            ) {
                // The felt, what is about to happen to it, and the shadow and
                // the white edge of everything resting on it.
                Canvas(Modifier.fillMaxSize()) {
                    drawTable(layout, camera.plane, camera.eye)
                    drawFelt(layout)
                    drawIndicator(play.carry?.intent, play.field, layout)
                    seats.filter { !it.carried }.forEach { seat ->
                        val pose = cards[seat.id]?.pose ?: seat.pose
                        drawCardShadow(pose, seat.width, seat.height, seat.height, seat.solid)
                    }
                    seats.filter { !it.carried }.forEach { seat ->
                        val pose = cards[seat.id]?.pose ?: seat.pose
                        drawSolidEdges(
                            pose, seat.width, seat.height, seat.solid,
                            camera.plane, camera.eye, seat.depth,
                        )
                    }
                }

                seats.filter { !it.carried }.forEach { seat ->
                    StagedCard(seat, cards, state, back, density, camera)
                }

                // Then the shadow of anything in the air, over the cards it
                // falls across rather than under them, and then the thing
                // casting it. Parent order is still the whole depth sort.
                Canvas(Modifier.fillMaxSize()) {
                    seats.filter { it.carried }.forEach { seat ->
                        val pose = cards[seat.id]?.pose ?: seat.pose
                        drawCardShadow(pose, seat.width, seat.height, seat.height, seat.solid)
                    }
                    // A card in the air is the one a hand is actually looking
                    // at, and it is the one whose thickness used to go missing:
                    // only the cards resting on the mat had their bodies drawn,
                    // so picking a card up made it flat.
                    seats.filter { it.carried }.forEach { seat ->
                        val pose = cards[seat.id]?.pose ?: seat.pose
                        drawSolidEdges(
                            pose, seat.width, seat.height, seat.solid,
                            camera.plane, camera.eye, seat.depth,
                        )
                    }
                }

                seats.filter { it.carried }.forEach { seat ->
                    StagedCard(seat, cards, state, back, density, camera)
                }
            }

            MatInput(pilot = pilot, machine = machine, layout = layout, camera = camera)

            menuFor?.let { origin ->
                CardActions(play, origin, onDismiss = { menuFor = null })
            }
        }

        PlayTopBar(play, camera, onBack, onGuide = { guide = true })

        // Over the bar as well as the table, because it is answering a question
        // about both of them.
        if (guide) PlayGuide(onDismiss = { guide = false })
    }
    }
}

/** One card, and everything the stage needs to know about drawing it. */
private data class Seat(
    val id: Int,
    val card: BoardCard,
    val pose: Pose3,
    val faceUp: Boolean,
    /** Off the mat, so it draws on the flat layer above rather than the plane. */
    val carried: Boolean,
    /**
     * Position assigned from the finger rather than sprung toward it.
     *
     * Not the same question as [carried], which is only about which layer draws
     * it. A peeked card is in the air and nobody is holding it, so it should
     * *rise*; pinning it would make it jump.
     */
    val pinned: Boolean = false,
    /** How many cards are here. One card is a card; more than one is a solid. */
    val depth: Int,
    val materials: Int,
    val counters: Int,
    val width: Float,
    val height: Float,
    /** Held up to be read rather than played, which changes the art it loads. */
    val magnified: Boolean = false,
) {
    /**
     * How far the body of this card, or this pile, hangs below its printed face.
     *
     * The pose already sits on top of it — a card resting on a deck is at the
     * *top* of the deck — so this is what the renderer extrudes downward to
     * reach the felt.
     */
    val solid: Float get() = CardSolid.pileDepth(depth, width)
}

/**
 * Every card that is visible, and where it belongs.
 *
 * Ordered back to front by depth on the mat, with recency breaking ties —
 * `PlayField.mat` is ordered by recency alone, which is right for two cards in
 * the same place and wrong for two at different depths, because on a tilted
 * plane a card played early near the front must still occlude one played later
 * further back.
 */
private fun seatsFor(
    field: PlayField,
    layout: BoardLayout,
    carry: Carry?,
    peeking: DragOrigin? = null,
    /**
     * The three angles that, inside the mat's own layer, come out square to the
     * viewer. Passed in rather than computed here because it is a fact about
     * where the camera is, and this function is about where the cards are.
     */
    facingReader: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
): List<Seat> {
    val seats = mutableListOf<Seat>()
    val cardWidth = layout.cardWidth
    val cardHeight = layout.cardHeight

    fun poseAt(at: MatPoint, z: Float, turned: Boolean, faceUp: Boolean, lean: Float = 0f): Pose3 {
        val (x, y) = layout.toPixels(at)
        return Pose3(
            position = Vec3(x, y, z),
            rotX = lean,
            // One truth about which face shows: half a turn means the back.
            rotY = if (faceUp) 0f else 180f,
            rotZ = if (turned) -90f else 0f,
            scale = 1f,
        )
    }

    field.mat.forEach { placed ->
        val carrying = carry?.id == placed.id
        // In the air, the carry owns which face shows: it is what the card will
        // land as, so turning it over is visible before it is committed.
        val faceUp = if (carrying) !carry.faceDown else placed.faceUp
        seats += Seat(
            id = placed.id,
            card = placed.card,
            pose = poseAt(
                at = if (carrying) carry.at else placed.at,
                // Resting on whatever is under it, which for a stack is the
                // rest of the stack: a card on a pile of four is four cards
                // off the felt, and its shadow is cast from up there.
                z = if (carrying) {
                    cardHeight * LIFT_Z
                } else {
                    CardSolid.pileDepth(placed.depth, cardWidth)
                },
                turned = placed.turned || (carrying && carry.quarterTurns % 2 != 0),
                faceUp = faceUp,
            ),
            faceUp = faceUp,
            carried = carrying,
            pinned = carrying,
            depth = placed.depth,
            materials = placed.card.materials.size,
            counters = placed.card.counters,
            width = cardWidth,
            height = cardHeight,
        )
    }

    // Depth first, recency second. `mat` is ordered by recency alone, which is
    // right for two cards in the same place and wrong for two at different
    // depths: on a tilted plane a card played early near the front must still
    // occlude one played later further back.
    seats.sortWith(compareBy({ quantised(it.pose.position.y, layout.field.height) }, { it.id }))

    // The hand, fanned along the band the solver set aside.
    field.hand.forEachIndexed { index, card ->
        val carrying = carry?.from is DragOrigin.Hand &&
            (carry.from as DragOrigin.Hand).index == index
        val at = handPointFor(layout, index, field.hand.size)
        // A card in hand is always face-up to its owner; one turned over on the
        // way out of it is being set.
        val faceUp = !(carrying && carry.faceDown)
        seats += Seat(
            id = card.instanceId,
            card = card,
            pose = poseAt(
                at = if (carrying) carry.at else at,
                // Not zero. A leaned card pivots on its bottom edge, and this
                // is what buys that — see [handLiftOf].
                z = if (carrying) {
                    cardHeight * HAND_LIFT
                } else {
                    handLiftOf(cardHeight, CardSolid.pileDepth(1, cardWidth))
                },
                turned = carrying && carry.quarterTurns % 2 != 0,
                faceUp = faceUp,
                lean = if (carrying) 0f else HAND_LEAN,
            ),
            faceUp = faceUp,
            carried = carrying,
            pinned = carrying,
            depth = 1,
            materials = 0,
            counters = 0,
            width = cardWidth,
            height = cardHeight,
        )
    }

    // The tops of the piles, so a stack is a card and a number the way a stack is.
    listOf(
        BoardSlot.Deck to field.deck,
        BoardSlot.ExtraDeck to field.extraDeck,
        BoardSlot.Graveyard to field.graveyard,
        BoardSlot.Banished to field.banished,
    ).forEach { (slot, pile) ->
        val top = pile.firstOrNull() ?: return@forEach
        val rect = layout[slot] ?: return@forEach
        val faceUp = slot == BoardSlot.Graveyard || slot == BoardSlot.Banished
        seats += Seat(
            id = top.instanceId,
            card = top,
            pose = Pose3(
                // On top of its own pile, which is the whole reason a deck
                // looks like a deck rather than like a card with a number.
                position = Vec3(
                    rect.centerX,
                    rect.centerY,
                    CardSolid.pileDepth(pile.size, cardWidth),
                ),
                rotY = if (faceUp) 0f else 180f,
            ),
            faceUp = faceUp,
            carried = false,
            depth = pile.size,
            materials = 0,
            counters = 0,
            width = cardWidth,
            height = cardHeight,
        )
    }

    // A card being carried out of a pile has no seat of its own yet — it is
    // still in the pile as far as the field is concerned — so it gets one here,
    // or dragging out of the graveyard would carry something invisible.
    val from = carry?.from
    if (from is DragOrigin.Pile) {
        val pile = when (from.pile) {
            BoardSlot.Deck -> field.deck
            BoardSlot.ExtraDeck -> field.extraDeck
            BoardSlot.Graveyard -> field.graveyard
            BoardSlot.Banished -> field.banished
            is BoardSlot.Zone -> emptyList()
        }
        pile.getOrNull(from.index)?.let { card ->
            val faceUp = !carry.faceDown
            seats += Seat(
                id = card.instanceId,
                card = card,
                pose = poseAt(carry.at, cardHeight * LIFT_Z, carry.quarterTurns % 2 != 0, faceUp),
                faceUp = faceUp,
                carried = true,
                pinned = true,
                depth = 1,
                materials = 0,
                counters = 0,
                width = cardWidth,
                height = cardHeight,
            )
        }
    }

    // The peek, applied last as a change to one seat rather than threaded
    // through every branch above: it moves nothing and owns nothing, it only
    // says that one card is being read right now.
    val peeked = peeking?.let { seatIdOf(field, it) }
    if (peeked != null) {
        val index = seats.indexOfFirst { it.id == peeked }
        if (index >= 0) {
            val seat = seats[index]
            seats[index] = seat.copy(
                pose = seat.pose.copy(
                    position = Vec3(
                        seat.pose.position.x,
                        seat.pose.position.y,
                        cardHeight * PEEK_Z,
                    ),
                    // Square to the reader and face up, whichever way it was
                    // lying. Being unable to check your own set card is the
                    // thing a held finger is asking about — and "the reader" is
                    // now wherever the camera is, so this is whatever undoes it
                    // rather than a pair of zeroes.
                    rotX = facingReader.first,
                    rotY = facingReader.second,
                    rotZ = facingReader.third,
                    scale = PEEK_SCALE,
                ),
                faceUp = true,
                carried = true,
                pinned = false,
                magnified = true,
            )
        }
    }

    return seats
}

/** Which card on the stage a grabbed origin refers to. */
private fun seatIdOf(field: PlayField, origin: DragOrigin): Int? = when (origin) {
    is DragOrigin.Mat -> origin.id
    is DragOrigin.Hand -> field.hand.getOrNull(origin.index)?.instanceId
    is DragOrigin.Pile -> when (origin.pile) {
        BoardSlot.Deck -> field.deck
        BoardSlot.ExtraDeck -> field.extraDeck
        BoardSlot.Graveyard -> field.graveyard
        BoardSlot.Banished -> field.banished
        is BoardSlot.Zone -> emptyList()
    }.getOrNull(origin.index)?.instanceId
}

/** Quantised so a small wobble in y cannot reshuffle the whole paint order. */
private fun quantised(y: Float, height: Float): Int =
    if (height <= 0f) 0 else (y / (height * 0.02f)).toInt()

/**
 * Where hand card [index] of [count] sits.
 *
 * A row that fans rather than an arc that curls: an arc is lovely at six cards
 * and unreadable at fourteen, and a combo line routinely holds fourteen. The
 * cards overlap only as far as they must, so the common case still bows.
 */
internal fun handPointFor(layout: BoardLayout, index: Int, count: Int): MatPoint {
    val band = layout.hand
    val step = if (count <= 1) {
        0f
    } else {
        min(layout.cardWidth * 0.62f, (band.width - layout.cardWidth) / (count - 1))
    }
    val spread = layout.cardWidth + step * (count - 1)
    val x = band.left + (band.width - spread) / 2f + layout.cardWidth / 2f + index * step

    return MatPoint(
        x = if (layout.field.width > 0f) (x - layout.field.left) / layout.field.width else 0.5f,
        y = if (layout.field.height > 0f) {
            (band.centerY - layout.field.top) / layout.field.height
        } else {
            1f
        },
    )
}

/**
 * Where the card in the air is going to land, drawn on the mat under it.
 *
 * The indicator and the outcome are the same value — `DropTargets` decided it
 * once and both the highlight and the release read that decision — so the table
 * cannot promise one thing and do another.
 */
private fun DrawScope.drawIndicator(
    intent: DropIntent?,
    field: PlayField,
    layout: BoardLayout,
) {
    if (intent == null) return

    val accent = MasterToolPalette.AccentBright
    fun ring(rect: Slot, alpha: Float, weight: Float = 0.035f) {
        drawRoundRect(
            color = accent.copy(alpha = alpha),
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(rect.width * 0.05f),
            style = Stroke(width = rect.width * weight),
        )
    }

    /** The footprint a card would occupy centred on a mat point. */
    fun footprint(at: MatPoint): Slot {
        val (x, y) = layout.toPixels(at)
        return Slot(
            left = x - layout.cardWidth / 2f,
            top = y - layout.cardHeight / 2f,
            width = layout.cardWidth,
            height = layout.cardHeight,
        )
    }

    fun onCard(id: Int, alpha: Float, weight: Float) {
        field.placed(id)?.let { ring(footprint(it.at), alpha, weight) }
    }

    when (intent) {
        is DropIntent.Zone -> layout[intent.slot]?.let { ring(it, 0.85f) }
        DropIntent.Graveyard -> layout[BoardSlot.Graveyard]?.let { ring(it, 0.85f) }
        DropIntent.Banish -> layout[BoardSlot.Banished]?.let { ring(it, 0.85f) }
        DropIntent.Deck -> layout[BoardSlot.Deck]?.let { ring(it, 0.85f) }
        DropIntent.ExtraDeck -> layout[BoardSlot.ExtraDeck]?.let { ring(it, 0.85f) }
        DropIntent.Hand -> ring(layout.hand, 0.6f)

        // The freeform three, which are the only ones with no box already drawn
        // on the felt to light up. Stacking rings the card being landed on;
        // attaching rings it thinner, because the card is going underneath;
        // free just shows the footprint, so "nowhere in particular" still looks
        // like a decision rather than a dead gesture.
        is DropIntent.Stack -> onCard(intent.onto, 0.85f, 0.035f)
        is DropIntent.Attach -> onCard(intent.onto, 0.7f, 0.012f)
        is DropIntent.Free -> ring(footprint(intent.at), 0.28f, 0.02f)

        DropIntent.Cancel -> Unit
    }
}

/**
 * One card on the stage.
 *
 * Both faces are always composed, each setting its own alpha inside its own
 * `graphicsLayer`. Picking a face by reading the angle in the composable body
 * would recompose the card on every frame of a flip, which is the thing the
 * whole one-loop pattern exists to avoid — and you cannot branch inside a
 * `graphicsLayer` block, because it is a draw lambda, not composition.
 */
@Composable
private fun StagedCard(
    seat: Seat,
    cards: MutableMap<Int, StageCard>,
    state: DeckBuilderState,
    back: com.kaiharimoto.mastertool.ui.components.CardBackChoice,
    density: androidx.compose.ui.unit.Density,
    camera: StageCameraState,
) {
    val motion = cards[seat.id] ?: return
    val art = state.index.byId(seat.card.cardId)

    with(density) {
        Box(
            Modifier
                .size(seat.width.toDp(), seat.height.toDp())
                .graphicsLayer {
                    val pose = motion.pose
                    // One frame, one answer. Every card on this stage — resting
                    // on the felt, sitting on top of a deck, or held in the air
                    // — is drawn inside the mat's own layer, so its height is
                    // folded into a point on the felt that will *look* like that
                    // height once the camera has run. That is what puts the top
                    // card of a deck on top of the deck, and it is what keeps a
                    // card in your hand turning with the table it left.
                    val flattened = camera.plane.flatten(pose.position)

                    translationX = flattened.x - seat.width / 2f
                    translationY = flattened.y - seat.height / 2f
                    rotationX = pose.rotX
                    rotationY = pose.rotY
                    rotationZ = pose.rotZ
                    scaleX = flattened.scale * pose.scale
                    scaleY = flattened.scale * pose.scale
                    cameraDistance = (seat.width * 6f) / this.density
                },
        ) {
            CardFace(
                art = art,
                faceUp = true,
                back = back,
                motion = motion,
                camera = camera,
                magnified = seat.magnified,
            )
            CardFace(art = art, faceUp = false, back = back, motion = motion, camera = camera)

            if (seat.counters > 0 || seat.materials > 0) {
                Badge(
                    text = if (seat.counters > 0) "●${seat.counters}" else "◈${seat.materials}",
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

/**
 * One side of a card.
 *
 * Its own layer, whose alpha is a function of the turn — so a flip swaps face
 * for back at exactly ninety degrees, where the card is edge-on and neither is
 * visible anyway, without composition being involved at all.
 */
@Composable
private fun CardFace(
    art: Card?,
    faceUp: Boolean,
    back: com.kaiharimoto.mastertool.ui.components.CardBackChoice,
    motion: StageCard,
    camera: StageCameraState,
    /** Held up to be read, so it is worth fetching the art at full size. */
    magnified: Boolean = false,
) {
    // Two surfaces, two materials. The printed side is card stock — foiled if
    // this is an extra-deck frame — and the other side is the back of a sleeve,
    // which is the quietest thing on the table. Each side owns its own, so a
    // card turning over changes what the light does to it at the same moment it
    // changes what you can see.
    val material = remember(art, faceUp) { CardStock.of(art, faceUp) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val turn = motion.pose.rotY * (PI.toFloat() / 180f)
                val facing = cos(turn)
                alpha = if (faceUp) {
                    if (facing > 0f) 1f else 0f
                } else {
                    if (facing > 0f) 0f else 1f
                }
                // The back is drawn mirrored, or it would read as reversed once
                // the card has turned to show it.
                if (!faceUp) rotationY = 180f
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(CardCornerRadius))
                .background(MasterToolPalette.SurfaceRaised),
        ) {
            if (faceUp) {
                if (art != null) {
                    Text(
                        art.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(2.dp),
                    )
                    AsyncImage(
                        // The thumbnail is right for a card lying on the table
                        // and visibly soft at nearly twice the size, which is
                        // exactly when someone is trying to read it.
                        model = if (magnified) {
                            art.imageUrl ?: art.imageUrlSmall
                        } else {
                            art.imageUrlSmall ?: art.imageUrl
                        },
                        contentDescription = art.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                CardBack(Modifier.fillMaxSize(), back.style, back.imageUrl)
            }

            // The light, over the picture and inside the card's own clip, so
            // the pool and the rim follow the cut corners. The pose is read
            // *here*, in a draw lambda, which is what keeps a card catching the
            // light through a whole flip without recomposing anything.
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawCardSurface(
                        pose = motion.pose,
                        material = material,
                        eye = camera.eye,
                        radiusPx = CardCornerRadius.toPx(),
                    )
                },
            )
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MasterToolPalette.Ink.copy(alpha = 0.82f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = MasterToolPalette.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayTopBar(
    play: PlayState,
    camera: StageCameraState,
    onBack: () -> Unit,
    onGuide: () -> Unit,
) {
    val feedback = LocalFeedback.current
    Row(
        Modifier.fillMaxWidth().height(TOP_BAR).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BarButton("← Deck", onClick = onBack)
        // Second from the left, where a thing you need in the first minute
        // belongs. The table has no affordances on it by design, so this button
        // is the entire discoverability budget of every gesture on the stage —
        // it is not going at the far end of a row of fifteen.
        BarButton("? Guide", onClick = onGuide)
        Divider()
        Text(
            play.field.lifePoints.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (play.field.lifePoints <= 0) MasterToolPalette.Danger else Color.Unspecified,
        )
        listOf(-1000, -500, -100, 100).forEach { delta ->
            BarButton(if (delta > 0) "+$delta" else "$delta") {
                play.move { it.adjustLifePoints(delta) }
            }
        }
        Divider()
        // The seats, as the touch idiom for the camera. A drag on bare felt
        // turns the table and a key puts it back, but neither of those is
        // discoverable, and a row of three words is.
        StageSeat.entries.forEach { seat ->
            BarButton(seat.label) { camera.rig.aimAt(seat) }
        }
        Divider()
        BarButton(play.field.phase.label) { play.move { it.nextPhase() } }
        BarButton("Turn ${play.field.turn} ⟳") { play.move { it.endTurn() } }

        Box(Modifier.weight(1f))

        play.announcement?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MasterToolPalette.AccentBright,
            )
        }
        BarButton("Draw") {
            if (play.move { it.draw() }) feedback.play(SoundEffect.DEAL, Haptic.DEAL)
        }
        BarButton("Shuffle") {
            if (play.move { it.shuffleDeck(it.turn * 31L + it.deck.size) }) {
                feedback.play(SoundEffect.SHUFFLE, Haptic.SHUFFLE)
            }
        }
        BarButton("Undo", enabled = play.canUndo) { play.undo() }
        BarButton("Redo", enabled = play.canRedo) { play.redo() }
        BarButton("New hand") { play.restart() }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun BarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(2.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            maxLines = 1,
        )
    }
}

/** Whether a point in mat pixels is inside a rectangle, with a little grace. */
internal fun Slot.holds(x: Float, y: Float, grace: Float = 0f): Boolean =
    x >= left - grace && x <= right + grace && y >= top - grace && y <= bottom + grace


/**
 * Whether [CardActions] would have anything to put in a menu about this.
 *
 * The one place that answer is decided, so the check and the menu cannot drift
 * apart into a gesture that opens an empty box.
 */
private fun hasMenu(origin: DragOrigin): Boolean = when (origin) {
    is DragOrigin.Mat -> true
    // The deck's menu is the deck's. Every other pile has nothing to offer that
    // holding it or dragging off it does not already do better.
    is DragOrigin.Pile -> origin.pile == BoardSlot.Deck
    is DragOrigin.Hand -> false
}

/**
 * Everything a card can be told to do that is not a movement.
 *
 * Behind the two-finger hold, which keeps every one-finger gesture purely about
 * where a card is. The pointer idiom is a right-click on the same card, and the
 * keyboard one is the shortcut table — three producers, one menu.
 */
@Composable
private fun CardActions(play: PlayState, origin: DragOrigin, onDismiss: () -> Unit) {
    val id = (origin as? DragOrigin.Mat)?.id

    androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        // A lambda rather than a local `fun`, because a local function cannot be
        // @Composable and DropdownMenuItem is one. Every entry below dismisses
        // first and acts second, so the menu never outlives the card it is about.
        val item: @Composable (String, () -> Unit) -> Unit = { label, action ->
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label) },
                onClick = { onDismiss(); action() },
            )
        }

        if (id != null) {
            val placed = play.field.placed(id)
            item("Turn over") { play.move { it.flip(id) } }
            item("Rotate") { play.move { it.rotate(id) } }
            item("Bring to front") { play.move { it.bringToFront(id) } }
            androidx.compose.material3.HorizontalDivider()
            item("To graveyard") { play.move { it.toGraveyard(id) } }
            item("Banish") { play.move { it.toBanish(id) } }
            item("Banish face-down") { play.move { it.toBanish(id, faceDown = true) } }
            item("To hand") { play.move { it.toHand(id) } }
            item("To deck top") { play.move { it.toDeckTop(id) } }
            item("To deck bottom") { play.move { it.toDeckBottom(id) } }
            androidx.compose.material3.HorizontalDivider()
            if ((placed?.depth ?: 1) > 1) {
                item("Take the top card off") {
                    play.move { field -> field.unstack(id, placed!!.at.let { it.copy(x = it.x + 0.06f) }) }
                }
            }
            if ((placed?.card?.materials?.size ?: 0) > 0) {
                item("Detach a material") { play.move { it.detachMaterial(id) } }
            }
            item("Add a counter") { play.move { it.addCounter(id, 1) } }
            if ((placed?.card?.counters ?: 0) > 0) {
                item("Remove a counter") { play.move { it.addCounter(id, -1) } }
            }
        } else if (origin is DragOrigin.Pile) {
            item("Shuffle the deck") { play.move { it.shuffleDeck(it.turn * 31L + it.deck.size) } }
            item("Draw") { play.move { it.draw() } }
        }
    }
}
