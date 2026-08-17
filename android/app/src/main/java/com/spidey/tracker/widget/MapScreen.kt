package com.spidey.tracker.widget

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

@Composable
fun MapScreen(state: UiState, model: SpideyViewModel) {
    Box(Modifier.fillMaxSize().background(Ink.bezel)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {

            // ---- hardware row ----------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundButton(onClick = { model.setTab(Tab.PATROL) }) { HamburgerIcon() }
                TitlePlate()
                SquareButton(onClick = { model.setTab(Tab.BUGLE) }) {
                    PixelSpider(size = 20.dp, body = Ink.navyDeep, legs = Ink.navyDeep)
                }
            }

            // ---- the screen -------------------------------------------------
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Ink.navyDeep)
                    .padding(2.dp)
                    .background(Ink.bezelLight)
                    .padding(2.dp)
                    .background(Ink.navyDeep),
            ) {
                Column(Modifier.fillMaxSize()) {
                    RulerEdge(vertical = false)
                    Row(Modifier.weight(1f)) {
                        RulerEdge(vertical = true)
                        // Clipped: the embedded MapView is a real Android view and
                        // will happily paint past its slot over the frame.
                        Box(Modifier.weight(1f).fillMaxSize().clipToBounds()) {
                            OsmMap(state, model)
                            MapFurniture(state, model)
                        }
                        RulerEdge(vertical = true)
                    }
                    RulerEdge(vertical = false)
                }

                // Filter tabs hang off the left edge, half outside the screen.
                Column(
                    Modifier.align(Alignment.CenterStart).offset(x = (-10).dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    val now = state.clock
                    EdgeTab(
                        Ink.pinGreen,
                        Ink.pinGreenDark,
                        state.live.count { SpideyCore.heatOf(it, now) == SpideyCore.Heat.WARM },
                    )
                    EdgeTab(
                        Ink.pinRed,
                        Ink.pinRedDark,
                        state.live.count { SpideyCore.heatOf(it, now) == SpideyCore.Heat.HOT },
                    )
                }
            }

            // ---- share bar ---------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WatcherSprite(size = 40.dp, modifier = Modifier.padding(end = 6.dp))

                Box(
                    Modifier
                        .weight(1f)
                        .background(Ink.navyDeep)
                        .padding(2.dp)
                        .background(Ink.bezelLight)
                        .padding(2.dp)
                        .background(Ink.navyDeep)
                        .clickable { model.setReporting(true) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(Ink.bezelLight))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SHARE YOUR SPIDEY SIGHTING",
                            style = PixelType.small,
                            color = Ink.bezelLight,
                        )
                    }
                }

                Box(Modifier.padding(start = 6.dp)) {
                    SquareButton(
                        size = 46.dp,
                        fill = if (state.showHeat) Ink.bezelLight else Ink.muted,
                        onClick = { model.toggleHeat() },
                    ) {
                        SpeakerIcon()
                    }
                }
            }

            // ---- the two big buttons -----------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AmberButton("The Bugle", Modifier.weight(1f)) { model.setTab(Tab.BUGLE) }
                AmberButton(
                    if (state.activePatrol != null) "End Patrol" else "Start Patrol",
                    Modifier.weight(1f),
                ) {
                    if (state.activePatrol != null) model.stopPatrol() else model.startPatrol()
                }
            }
        }
    }

    if (state.reporting) ReportSheet(state, model)
}

/** Everything layered over the map: counter, callout, compass, sighting card. */
@Composable
private fun BoxScope.MapFurniture(state: UiState, model: SpideyViewModel) {
    val me = state.profile?.id
    val unexplored = state.live.count { sighting ->
        me == null || (
            sighting.confirms.none { it.userId == me } &&
                sighting.denies.none { it.userId == me }
            )
    }

    if (state.selectedId == null) {
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CounterStrip(total = state.live.size, unexplored = unexplored)

            if (unexplored > 0) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .background(Ink.amberInk)
                        .padding(2.dp)
                        .background(Ink.amber)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "$unexplored UNEXPLORED\n   SIGHTINGS",
                        style = PixelType.small,
                        color = Ink.amberInk,
                    )
                }
            }
        }
    }

    WebCompass(size = 92.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))

    state.selected?.let { sighting ->
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 6.dp, end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SightingCard(sighting, state, model)
            CardTail()
        }
    }
}

@Composable
private fun OsmMap(state: UiState, model: SpideyViewModel) {
    val context = LocalContext.current
    val overlay = remember { SightingOverlay() }
    val grid = remember { GraticuleOverlay() }

    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = java.io.File(context.cacheDir, "tiles")
        }

        MapView(context).apply {
            // Navy underneath, so a slow or failed tile load still reads as the
            // map rather than flashing osmdroid's default blue.
            setBackgroundColor(android.graphics.Color.parseColor("#0A2145"))
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
            )
            // Recolours the standard basemap into the deep navy the film's
            // version uses: crush saturation, then push everything into blue.
            overlayManager.tilesOverlay.setColorFilter(
                ColorMatrixColorFilter(
                    ColorMatrix(
                        floatArrayOf(
                            0.10f, 0.14f, 0.06f, 0f, 6f,
                            0.14f, 0.22f, 0.10f, 0f, 24f,
                            0.26f, 0.38f, 0.20f, 0f, 58f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                ),
            )
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(state.home.lat, state.home.lng))
            overlays.add(grid)
            overlays.add(overlay)

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    mapCenter.let {
                        model.setMapCentre(SpideyCore.LatLng(it.latitude, it.longitude))
                    }
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean = false
            })
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            // Releases the tile provider's threads and cache handles.
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            overlay.update(
                sightings = state.live,
                now = state.clock,
                selectedId = state.selectedId,
                onSelect = { model.select(it) },
            )

            map.overlays.removeAll { it is Polyline }
            state.activePatrol?.route?.takeIf { it.size > 1 }?.let { route ->
                map.overlays.add(
                    0,
                    Polyline(map).apply {
                        setPoints(route.map { GeoPoint(it.lat, it.lng) })
                        outlinePaint.color = android.graphics.Color.parseColor("#4EA9E8")
                        outlinePaint.strokeWidth = 7f
                    },
                )
            }

            map.invalidate()
        },
    )
}

/** The pin readout, styled as the film's popup card. */
@Composable
private fun SightingCard(
    sighting: SpideyCore.Sighting,
    state: UiState,
    model: SpideyViewModel,
) {
    val now = state.clock
    val heat = SpideyCore.heatOf(sighting, now)
    val meta = SpideyCore.tagMeta(sighting.tag)
    val away = SpideyCore.distanceM(state.position ?: state.home, sighting.position)
    val fade = SpideyCore.msUntilNextBand(sighting, now)
    val me = state.profile?.id

    val myVote = when {
        sighting.confirms.any { it.userId == me } -> true
        sighting.denies.any { it.userId == me } -> false
        else -> null
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.navyDeep)
            .padding(2.dp)
            .background(Ink.bezelLight)
            .padding(2.dp)
            .background(Ink.navyDeep)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(heatColor(heat)).padding(horizontal = 5.dp, vertical = 3.dp)) {
                Text(
                    when (heat) {
                        SpideyCore.Heat.HOT -> "HOT"
                        SpideyCore.Heat.WARM -> "WARM"
                        SpideyCore.Heat.COLD -> "COLD"
                    },
                    style = PixelType.tiny,
                    color = Ink.navyDeep,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(meta.label.uppercase(), style = PixelType.small, color = Ink.white)
            Spacer(Modifier.weight(1f))
            Text(
                "X",
                style = PixelType.small,
                color = Ink.muted,
                modifier = Modifier.clickable { model.select(null) }.padding(4.dp),
            )
        }

        sighting.note?.let {
            Text(
                it,
                style = PixelType.tiny,
                color = Ink.text,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            "@${sighting.reporterHandle} · ${SpideyCore.formatDistance(away)} AWAY",
            style = PixelType.tiny,
            color = Ink.muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            "${sighting.confirms.size} CONFIRMED · ${sighting.denies.size} DISPUTED" +
                when {
                    fade == null -> ""
                    heat == SpideyCore.Heat.HOT -> " · COOLS ${SpideyCore.formatMinutes(fade)}"
                    else -> " · COLD IN ${SpideyCore.formatMinutes(fade)}"
                },
            style = PixelType.tiny,
            color = Ink.muted,
            modifier = Modifier.padding(top = 5.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PanelButton(
                "I saw it too",
                Modifier.weight(1f),
                accent = if (myVote == true) Ink.pinGreen else Ink.bezelLight,
            ) { model.vote(sighting.id, true) }

            PanelButton(
                "Nothing here",
                Modifier.weight(1f),
                accent = if (myVote == false) Ink.pinRed else Ink.bezelLight,
            ) { model.vote(sighting.id, false) }
        }
    }
}
