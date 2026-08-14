package com.kaiharimoto.mastertool.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.layout.CameraRig
import com.kaiharimoto.mastertool.core.scene.Scenery
import com.kaiharimoto.mastertool.core.tune.Knob
import com.kaiharimoto.mastertool.core.tune.StageKnobs
import com.kaiharimoto.mastertool.core.tune.StageTuning
import com.kaiharimoto.mastertool.core.tune.TuningCodec
import com.kaiharimoto.mastertool.core.tune.TuningSurface
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The instrument: every number somebody has argued about, live, on the device
 * they will be judged on.
 *
 * ## Why it is a panel and not a sheet
 *
 * The builder uses `ModalBottomSheet` and the play stage deliberately does not.
 * `PlayGuide` states the reason and it applies twice as hard here: the mat owns
 * one `pointerInput` covering the whole stage and reads raw pointer events, so
 * a sheet animating in over it leaves the gesture arbiter holding a gesture it
 * will never see the end of. A scrim is no better — a tuning panel that hides
 * the thing being tuned is a tuning panel nobody can use.
 *
 * So: an opaque column docked to one edge, drawn in the *outer* box after the
 * bar. Compose hit-tests siblings in reverse order, which is what stops a
 * slider drag from orbiting the table underneath it — and the root consumes
 * every pointer it is given rather than trusting that, because "underneath this
 * panel is the single gesture arbiter" is not a thing to be casual about.
 *
 * ## Why the sliders are write-only
 *
 * A slider bound both ways to the live camera is a feedback loop with the orbit
 * gesture, and reading `camera.pose` in a composable body recomposes sixty cards
 * a frame — `StageCameraState` says so at length. So these write, and **Read
 * camera** is how a pose you orbited to gets *into* the panel.
 *
 * ## Why a slider hands back one field and not a document
 *
 * [onTune] takes a [Knob] and a number rather than a finished [StageTuning], and
 * that shape is the whole of a bug that made the panel very nearly unusable.
 *
 * A knob's track is a `pointerInput`, and a `pointerInput` block is started once
 * and keyed — here on `knob.path`, which never changes. So the gesture coroutine
 * keeps forever the lambda it captured the first time it was installed, and a
 * lambda that closed over the *document* closed over the document as it stood
 * when the panel opened. Every drag then wrote `documentAtOpen.copy(thisField)`,
 * and every other knob moved since went back to where it had been: move Focal
 * length and Distance reverted, move Distance and the lens reverted. All
 * twenty-seven of them, which is what it was reported as. The readout was
 * innocent — that is an ordinary composition read, so the numbers were right up
 * until the moment a second slider was touched.
 *
 * `rememberUpdatedState` below is the local repair, and `DragSource` has been
 * making it for the same reason for as long as there has been a drag. Handing
 * back a field is the structural one: a stale lambda cannot carry a stale
 * document if it never carries a document at all, and the read-modify-write
 * happens inside the preferences transform where the live one is.
 *
 * ## Why it is in the shipped build
 *
 * Not preference — necessity. A debug APK cannot install over the signed one, so
 * a debug-only panel is a panel that can never be opened on the tablet. It is
 * hidden instead: a long press on the life-point number, which is the one
 * element in the bar that is pure output. Deliberately absent from
 * `ShortcutTable` and `MatGuide` — the table *is* the help sheet, so anything in
 * it is documented by construction, and an instrument is not a feature that owes
 * anybody both idioms.
 */
@Composable
internal fun StageTuner(
    tuning: StageTuning,
    /** One knob, one number, applied to whatever the document is by then. */
    onTune: (Knob, Float) -> Unit,
    /**
     * And the whole document, for the two buttons that legitimately replace it.
     *
     * Safe where a slider is not: these are `onClick` lambdas, which Compose
     * hands the node afresh on every composition, so they see the document that
     * is there rather than the one that was.
     */
    onReplace: (StageTuning) -> Unit,
    camera: StageCameraState,
    surface: TuningSurface,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<String?>(null) }
    val muted = MasterToolPalette.TextMuted

    // The camera is placed by `PlayScreen`'s own `SideEffect`, not from here.
    // One writer, and a synchronous one: two places calling `placeAt` would race
    // over the same rig, and a coroutine-dispatched one straddles a frame — see
    // the note there, which cost a run of the studio its determinism.

    Column(
        modifier = modifier
            .width(PANEL_WIDTH)
            // The same ground the bar stands on, and opaque. A translucent wash
            // over content is the second line of the handbook's anti-patterns,
            // and this one sits over the thing it is describing.
            .background(MasterToolPalette.Ink)
            // The stage's single arbiter is directly underneath. Sibling order
            // already puts this in front of it, but a panel that leaked one
            // pointer through would orbit the table while you dragged a slider,
            // and the failure would look like the slider being wrong.
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> change.consume() }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tune", style = MaterialTheme.typography.titleSmall, color = MasterToolPalette.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BarButton("Read camera") {
                    onReplace(
                        tuning.copy(
                            camera = tuning.camera.reading(camera.pose),
                        ),
                    )
                }
                BarButton("Reset") { onReplace(StageTuning.DEFAULT) }
                BarButton("Close", onClick = onClose)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            StageKnobs.GROUPS.forEach { group ->
                Text(
                    group.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                StageKnobs.of(group).forEach { knob ->
                    KnobRow(
                        knob = knob,
                        value = knob.get(tuning),
                        extra = extraFor(knob, tuning, camera.rig),
                        onChange = { onTune(knob, it) },
                    )
                }
            }
        }

        val changed = TuningCodec.changedIn(tuning)
        Text(
            if (changed.isEmpty()) "Nothing moved yet." else "${changed.size} moved",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )

        val clipboard = LocalClipboardManager.current
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            BarButton("Copy JSON") {
                clipboard.setText(AnnotatedString(TuningCodec.export(tuning, surface)))
            }
            BarButton(if (shown == null) "Show JSON" else "Hide") {
                shown = if (shown == null) TuningCodec.export(tuning, surface) else null
            }
        }

        // The always-available fallback. Copying off a tablet into a chat window
        // is where a clipboard actually fails, and a screenshot of selectable
        // text is worse than a paste and better than nothing. Same move the
        // crash reporter makes, for the same reason.
        shown?.let { json ->
            SelectionContainer {
                Text(
                    json,
                    style = MaterialTheme.typography.bodySmall,
                    color = MasterToolPalette.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(JSON_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * One number: its name, what it is now, and a track to drag.
 *
 * A hand-rolled track rather than a `Slider`, and that is not invented-here. A
 * Material slider owns its own drag gesture and its own thumb animation, and
 * this one has to consume every pointer it is given because of what is
 * underneath it. Drawing a rectangle and reading a horizontal drag is fewer
 * moving parts than fighting one.
 */
@Composable
private fun KnobRow(
    knob: Knob,
    value: Float,
    extra: String?,
    onChange: (Float) -> Unit,
) {
    var width by remember { mutableStateOf(1f) }
    val fraction = knob.fractionOf(value)
    val muted = MasterToolPalette.TextMuted

    // The two gesture coroutines below are keyed on the knob's path, which never
    // changes, so they are installed once and never restarted — and they would
    // otherwise call whichever `onChange` existed at that moment for the rest of
    // the panel's life. `DragSource` states the rule; this row is where ignoring
    // it cost every knob on the panel. It is belt to the braces of `onChange`
    // now carrying a number rather than a document, and both are worth having.
    val currentOnChange by rememberUpdatedState(onChange)

    Column(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(knob.label, style = MaterialTheme.typography.bodySmall, color = MasterToolPalette.Text)
            Text(
                buildString {
                    if (knob.unit == "f/") append(knob.unit)
                    append(TuningCodec.trim(value))
                    if (knob.unit != "f/" && knob.unit.isNotEmpty()) {
                        if (knob.unit.length > 1) append(' ')
                        append(knob.unit)
                    }
                    extra?.let { append("  ").append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                // Moved values are white and untouched ones are grey, so the
                // diff you are about to paste back is legible at a glance.
                color = if (value == knob.get(StageTuning.DEFAULT)) muted else MasterToolPalette.Text,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(TRACK_HEIGHT)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                // Measured by layout rather than sampled inside the gesture
                // block, which runs once. A panel that is re-measured — and it
                // is, the moment the JSON is shown underneath it — would
                // otherwise leave every track mapping a stale width.
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(knob.path) {
                    detectDragGestures(
                        onDragStart = { at: Offset -> currentOnChange(knob.valueAt(at.x / width)) },
                    ) { change, _ ->
                        change.consume()
                        currentOnChange(knob.valueAt(change.position.x / width))
                    }
                }
                .pointerInput(knob.path) {
                    detectTapGestures { at -> currentOnChange(knob.valueAt(at.x / width)) }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(TRACK_HEIGHT)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)),
            )
        }
        Text(
            knob.note,
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
    }
}

/**
 * The second number a knob is worth saying out loud.
 *
 * A focal length in multiples of a shipped constant means nothing to anybody;
 * in millimetres it means what a lens means.
 *
 * And it is a function of the lens **alone**, which is the whole point of the
 * re-parameterisation behind it. The first version computed a field of view
 * from `distance × lens`, so touching the distance slider moved the number
 * beside the focal-length slider — which read, correctly, as one dial resetting
 * the other. Focal length is magnification; where you stand is a different
 * question and has its own row.
 */
private fun extraFor(knob: Knob, tuning: StageTuning, rig: CameraRig): String? = when (knob.path) {
    "camera.lens" -> "· ${(HOME_MM * tuning.camera.lens).roundToInt()}mm"
    // The clamp, said out loud on the slider it silently eats.
    //
    // The distance a person dials is not always the distance the camera takes:
    // `CameraEnvelope` solves a floor from the pitch and the mat's own diagonal,
    // and below it the number moves and the picture does not. That is the exact
    // report — "it goes below 1.4 and nothing happens" — and the whole of the
    // fault was that nothing said so. Shown only when it binds, because a limit
    // printed beside a value that is nowhere near it is noise.
    //
    // Solved rather than read back off the rig: this is a pure function of three
    // numbers the panel already has, and reading `camera.pose` here would be a
    // composable-body read of the live camera, which `StageCameraState` spends
    // thirty lines forbidding.
    "camera.distance" -> {
        val floor = tuning.camera.envelope().minDistanceAt(
            tuning.camera.pitchDegrees,
            rig.width,
            rig.height,
            // The pan too, or the readout under-reports whenever the camera is
            // aimed off the middle: the floor is solved against the corner
            // furthest from the *target*, so aiming away holds you out further.
            tuning.camera.panX,
            tuning.camera.panY,
        )
        if (floor > tuning.camera.distance + FLOOR_EPSILON) {
            "· held at ${TuningCodec.trim(floor)}"
        } else {
            null
        }
    }
    // What the shift is actually worth, in the only unit that means anything for
    // it: how far down the glass the camera's own target has moved. A number in
    // multiples of a governing dimension is unreadable; "a fifth of the way down
    // from the middle" is a picture. Solved from the surface rather than the
    // rig's live pose, for the reason "camera.distance" gives just above.
    "camera.shiftY" ->
        shiftReadout(tuning.camera.shiftY, rig.height, rig.width, rig.height, "down", "up")
    "camera.shiftX" ->
        shiftReadout(tuning.camera.shiftX, rig.width, rig.width, rig.height, "right", "left")
    "focus.strength" -> if (tuning.focus.strength <= 0f) "· off" else null
    // The coupling, said out loud on the slider that causes it. Two knobs add
    // into one distance, and a person moving the depth needs to see the wall
    // move without having to find the other row and do the arithmetic.
    "room.deskDepth", "room.wallBack" ->
        "· wall ${((Scenery.wallAt(tuning.room) * 100).roundToInt() / 100f)}"
    else -> null
}

/**
 * A shift, as a share of the surface it moves across.
 *
 * The knob's own unit is multiples of the governing dimension — `max(height,
 * width · 0.55)` — because that is what makes a saved pose mean the same thing
 * on a tablet and a monitor. It is also completely unreadable, and on a wide
 * stage it is not even the dimension the shift is moving along. This is the
 * same number said as a percentage of the edge it slides down.
 *
 * Silent at nothing, like the distance's floor: a panel that prints "· 0% down"
 * beside a slider sitting at zero is noise.
 */
private fun shiftReadout(
    shift: Float,
    along: Float,
    surfaceWidth: Float,
    surfaceHeight: Float,
    positive: String,
    negative: String,
): String? {
    if (shift == 0f || along <= 0f) return null
    val governing = max(surfaceHeight, surfaceWidth * 0.55f)
    val share = (shift * governing / along * 100f).roundToInt()
    if (share == 0) return null
    return if (share > 0) "· $share% $positive" else "· ${-share}% $negative"
}

/**
 * What the shipped lens is worth in the money everybody prices lenses in.
 *
 * Measured rather than chosen: at the home pose on a 1600-wide stage the
 * projection subtends 58 degrees horizontally, which on a 36mm frame is a 33mm
 * lens. Everything else on the dial is that times the magnification, because
 * focal length and magnification are the same number wearing two units.
 *
 * **And it is now true at every distance rather than only at the home one.** The
 * focal length used to carry `CameraPose.distance` as well as the lens, so the
 * field of view moved when the camera did — 21mm at the front of the envelope
 * and 57mm at the back — and this number was an honest measurement of one pose
 * printed beside a dial that meant something else everywhere else. `planeFor`
 * takes the distance off the lens now, so the millimetres are a function of this
 * dial alone. The constant did not move; what it describes stopped moving.
 */
private const val HOME_MM = 33f

/**
 * How far above the dialled value the floor has to be before it is worth saying.
 *
 * Half a step of the distance knob. Resting exactly on the floor is the normal
 * state of a slider pushed to its end, and "held at 1.17" beside a value of 1.17
 * would be the panel arguing with itself.
 */
private const val FLOOR_EPSILON = 0.005f

private val PANEL_WIDTH = 330.dp
private val TRACK_HEIGHT = 14.dp
private val JSON_HEIGHT = 220.dp

