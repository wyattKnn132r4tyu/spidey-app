package com.spidey.tracker.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun PatrolScreen(state: UiState, model: SpideyViewModel) {
    val total = state.totalDistanceM
    val (rank, next, progress) = SpideyRepository.rankFor(total)

    LazyColumn(Modifier.fillMaxSize().background(Ink.navyDeep).padding(horizontal = 12.dp)) {
        item {
            Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
                Text("@${state.profile?.handle ?: "..."}", style = PixelType.body, color = Ink.text)
                Text(
                    "FIELD RECORD",
                    style = PixelType.tiny,
                    color = Ink.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Ink.bezelLight)
                    .padding(2.dp)
                    .background(Ink.navy)
                    .padding(12.dp),
            ) {
                Text(rank.uppercase(), style = PixelType.label, color = Ink.amber)

                // A segmented bar reads as pixels rather than a smooth meter.
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val filled = (progress * 20).toInt()
                    repeat(20) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .background(if (i < filled) Ink.amber else Ink.navyDeep),
                        )
                    }
                }

                Text(
                    "${SpideyCore.formatDistance(total).uppercase()} COVERED",
                    style = PixelType.tiny,
                    color = Ink.text,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (next != null) {
                    Text(
                        "NEXT: ${next.uppercase()}",
                        style = PixelType.tiny,
                        color = Ink.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "${state.profile?.streakDays ?: 0} DAY STREAK · ${state.patrols.size} LOGGED",
                    style = PixelType.tiny,
                    color = Ink.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (state.locationDenied) {
            item {
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .background(Ink.amber)
                        .padding(2.dp)
                        .background(Ink.navy)
                        .padding(10.dp),
                ) {
                    Text(
                        "LOCATION OFF. ROUTES WILL NOT RECORD.\n" +
                            "PATROLS TRACK WHILE THE APP IS ON SCREEN.",
                        style = PixelType.tiny,
                        color = Ink.amber,
                    )
                }
            }
        }

        item {
            Box(Modifier.padding(top = 14.dp).fillMaxWidth()) {
                AmberButton(
                    if (state.activePatrol != null) {
                        "End patrol · ${SpideyCore.formatDistance(state.activePatrol.distanceM)}"
                    } else {
                        "Start patrol"
                    },
                    Modifier.fillMaxWidth(),
                    style = PixelType.body,
                ) {
                    if (state.activePatrol != null) model.stopPatrol() else model.startPatrol()
                }
            }
        }

        item {
            Text(
                "HISTORY",
                style = PixelType.small,
                color = Ink.bezelLight,
                modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
            )
        }

        if (state.patrols.isEmpty()) {
            item {
                Text(
                    "NO PATROLS YET.\nTHE CITY IS NOT GOING TO WATCH ITSELF.",
                    style = PixelType.tiny,
                    color = Ink.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        items(state.patrols, key = { it.id }) { patrol ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Ink.navy)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        SpideyCore.formatDistance(patrol.distanceM).uppercase(),
                        style = PixelType.small,
                        color = Ink.text,
                    )
                    Text(
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(patrol.startedAt)) +
                            " · " +
                            SpideyRepository.formatDuration(
                                (patrol.endedAt ?: state.clock) - patrol.startedAt,
                            ).uppercase(),
                        style = PixelType.tiny,
                        color = Ink.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "${patrol.sightingIds.size} LOGGED",
                    style = PixelType.tiny,
                    color = Ink.bezelLight,
                )
            }
        }

        item {
            Box(Modifier.padding(top = 24.dp, bottom = 28.dp).fillMaxWidth()) {
                PanelButton("Reset local data", Modifier.fillMaxWidth(), accent = Ink.pinRed) {
                    model.reset()
                }
            }
        }
    }
}
