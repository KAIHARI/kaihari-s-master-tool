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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.core.tune.Knob
import com.kaiharimoto.mastertool.core.tune.StageKnobs
import com.kaiharimoto.mastertool.core.tune.StageTuning
import com.kaiharimoto.mastertool.core.tune.TuningCodec
import com.kaiharimoto.mastertool.core.tune.TuningSurface
import com.kaiharimoto.mastertool.ui.theme.MasterToolPalette
import kotlin.math.roundToInt

/**
 * The instrument: fifteen numbers, live, on the device they will be judged on.
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
    onTune: (StageTuning) -> Unit,
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
                    onTune(
                        tuning.copy(
                            camera = com.kaiharimoto.mastertool.core.tune.CameraTune.of(camera.pose),
                        ),
                    )
                }
                BarButton("Reset") { onTune(StageTuning.DEFAULT) }
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
                        extra = extraFor(knob, tuning),
                        onChange = { onTune(knob.set(tuning, it)) },
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
                .pointerInput(knob.path) {
                    width = size.width.toFloat().coerceAtLeast(1f)
                    detectDragGestures(
                        onDragStart = { at: Offset -> onChange(knob.valueAt(at.x / width)) },
                    ) { change, _ ->
                        change.consume()
                        onChange(knob.valueAt(change.position.x / width))
                    }
                }
                .pointerInput(knob.path) {
                    width = size.width.toFloat().coerceAtLeast(1f)
                    detectTapGestures { at -> onChange(knob.valueAt(at.x / width)) }
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
private fun extraFor(knob: Knob, tuning: StageTuning): String? = when (knob.path) {
    "camera.lens" -> "· ${(HOME_MM * tuning.camera.lens).roundToInt()}mm"
    "focus.strength" -> if (tuning.focus.strength <= 0f) "· off" else null
    else -> null
}

/**
 * What the shipped lens is worth in the money everybody prices lenses in.
 *
 * Measured rather than chosen: at the home pose on a 1600-wide stage the
 * projection subtends 58 degrees horizontally, which on a 36mm frame is a 33mm
 * lens. Everything else on the dial is that times the magnification, because
 * focal length and magnification are the same number wearing two units.
 */
private const val HOME_MM = 33f

private val PANEL_WIDTH = 330.dp
private val TRACK_HEIGHT = 14.dp
private val JSON_HEIGHT = 220.dp

