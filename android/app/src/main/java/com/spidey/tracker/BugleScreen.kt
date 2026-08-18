package com.spidey.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun BugleScreen(state: UiState, model: SpideyViewModel) {
    val stories = Bugle.build(state.live, state.home, state.clock)

    LazyColumn(Modifier.fillMaxSize().background(Ink.navyDeep).padding(horizontal = 12.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("THE DAILY BUGLE", style = PixelType.title, color = Ink.text)
                Text(
                    "LATE CITY EDITION",
                    style = PixelType.tiny,
                    color = Ink.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(state.clock)),
                    style = PixelType.tiny,
                    color = Ink.muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Ink.bezelLight),
                )
            }
        }

        if (stories.isEmpty()) {
            item {
                Text(
                    "SLOW NEWS DAY.\nNOTHING ON THE WIRE.",
                    style = PixelType.small,
                    color = Ink.muted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        items(stories, key = { it.id }) { story ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .background(
                        when (story.tone) {
                            Bugle.Tone.ALARM -> Ink.pinRed
                            Bugle.Tone.WRY -> Ink.muted
                            Bugle.Tone.NEUTRAL -> Ink.bezelLight
                        },
                    )
                    .padding(2.dp)
                    .background(Ink.navy)
                    .clickable {
                        model.select(story.sightingIds.first())
                        model.setTab(Tab.MAP)
                    }
                    .padding(10.dp),
            ) {
                Text(
                    story.headline,
                    style = PixelType.body,
                    color = when (story.tone) {
                        Bugle.Tone.ALARM -> Ink.pinRed
                        Bugle.Tone.WRY -> Ink.muted
                        Bugle.Tone.NEUTRAL -> Ink.text
                    },
                )
                Text(
                    story.standfirst.uppercase(),
                    style = PixelType.tiny,
                    color = Ink.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "FILED ${SpideyCore.formatAgo(story.at, state.clock).uppercase()}",
                    style = PixelType.tiny,
                    color = Ink.bezelLight,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item {
            Text(
                "FOUR YEARS AND THE CITY STILL CANNOT NAME HIM.\nIT CAN ONLY SAY WHERE HE WAS.",
                style = PixelType.tiny,
                color = Ink.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }
    }
}
