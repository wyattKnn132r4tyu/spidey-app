package com.spidey.tracker.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(state: UiState, model: SpideyViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tag by remember { mutableStateOf(SpideyCore.TAGS.first().id) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = { model.setReporting(false) },
        sheetState = sheetState,
        containerColor = Ink.bezel,
        dragHandle = null,
    ) {
        ReportSheetContent(state, model, tag, note, { tag = it }, { note = it })
    }
}

/**
 * The sheet's contents, separate from the sheet itself so they can be rendered
 * and screenshotted on their own — a ModalBottomSheet lives in its own window,
 * which a screenshot of the activity cannot reach.
 */
@Composable
fun ReportSheetContent(
    state: UiState,
    model: SpideyViewModel,
    tag: String,
    note: String,
    onTag: (String) -> Unit,
    onNote: (String) -> Unit,
) {
    Column(Modifier.background(Ink.bezel).padding(8.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Ink.bezelDark)
                .padding(2.dp)
                .background(Ink.navyDeep)
                .padding(12.dp),
        ) {
            Text(
                "WHAT DID YOU SEE?",
                style = PixelType.label,
                color = Ink.text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )

            SpideyCore.TAGS.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { meta ->
                        TagButton(meta, meta.id == tag, Modifier.weight(1f)) { onTag(meta.id) }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }

            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .background(Ink.bezelLight)
                    .padding(2.dp)
                    .background(Ink.navy)
                    .padding(10.dp),
            ) {
                if (note.isEmpty()) {
                    Text("ADD A DETAIL (OPTIONAL)", style = PixelType.tiny, color = Ink.muted)
                }
                BasicTextField(
                    value = note,
                    onValueChange = { if (it.length <= 140) onNote(it) },
                    textStyle = PixelType.tiny.copy(color = Ink.text),
                    cursorBrush = SolidColor(Ink.amber),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                when {
                    state.position != null ->
                        "PINNED AT YOUR LOCATION" +
                            if (state.activePatrol != null) " · ON PATROL, COUNTS FOR MORE" else ""
                    state.mapCentre != null -> "NO LOCATION — PINS AT THE MAP CENTRE"
                    else -> "NO LOCATION — PINS AT THE DEFAULT SPOT"
                },
                style = PixelType.tiny,
                color = Ink.muted,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PanelButton("Cancel", Modifier.weight(1f)) { model.setReporting(false) }
                AmberButton("Drop pin", Modifier.weight(1f)) { model.report(tag, note) }
            }
        }
    }
}

@Composable
private fun TagButton(
    meta: SpideyCore.TagMeta,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(if (selected) Ink.amber else Ink.bezelDark)
            .padding(2.dp)
            .background(if (selected) Ink.navy else Ink.navyDeep)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelSpider(
            size = 14.dp,
            body = if (selected) Ink.amber else Ink.muted,
            legs = if (selected) Ink.amber else Ink.muted,
        )
        Text(
            meta.label.uppercase(),
            style = PixelType.tiny,
            color = if (selected) Ink.amber else Ink.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
