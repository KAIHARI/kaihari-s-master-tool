package com.kaiharimoto.mastertool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kaiharimoto.mastertool.ui.theme.LocalMasterToolColors

/**
 * Every panel in the program, wearing the same clothes.
 *
 * There are eight of these — the inspector, the deck check, statistics, filters,
 * siding, test hands, notes and the help sheet — and each had built its own
 * `ModalBottomSheet` with the same three lines of boilerplate and Material's own
 * looks. Which meant the deck sat on cloth and every panel that opened over it
 * was a grey slab with a grey pill on top.
 *
 * They now share this. The container is the mat, so a panel is the same surface
 * as the table it slides over rather than a different program's dialog; the
 * corner is 8dp rather than Material's 28, which is the same language as the
 * 6dp panes and 3dp cards; and the handle is a short accent bar instead of the
 * grey pill every Android app has.
 *
 * The sheet state is created here rather than by each caller, because not one of
 * them ever did anything with it except pass it back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterToolSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMasterToolColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.mat.base,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        dragHandle = {
            Box(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.accentBright.copy(alpha = 0.5f)),
                )
            }
        },
        content = content,
    )
}
