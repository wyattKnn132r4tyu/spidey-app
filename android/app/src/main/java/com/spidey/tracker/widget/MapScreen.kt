package com.spidey.tracker.widget

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
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
        Column(Modifier.fillMaxSize().padding(6.dp)) {

            // ---- title row -------------------------------------------------
            Row(
                Modifier.fillMaxWidth().height(46.dp).padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .background(Ink.amberDark)
                        .padding(2.dp)
                        .background(Ink.amber)
                        .clickable { model.setTab(Tab.PATROL) }
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(3) {
                            Box(Modifier.width(14.dp).height(2.dp).background(Ink.amberInk))
                        }
                    }
                }

                TitlePlate()

                Box(
                    Modifier
                        .background(Ink.bezelDark)
                        .padding(2.dp)
                        .background(Ink.white)
                        .clickable { model.setTab(Tab.BUGLE) }
                        .padding(6.dp),
                ) {
                    PixelSpider(size = 16.dp, body = Ink.navyDeep, legs = Ink.navyDeep)
                }
            }

            // ---- the screen itself -----------------------------------------
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Ink.bezelDark)
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
            }

            // ---- share bar --------------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WatcherSprite(size = 34.dp, modifier = Modifier.padding(end = 6.dp))

                Box(
                    Modifier
                        .weight(1f)
                        .background(Ink.bezelLight)
                        .padding(2.dp)
                        .background(Ink.navyDeep)
                        .clickable { model.setReporting(true) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "▪ SHARE YOUR SPIDEY SIGHTING",
                        style = PixelType.small,
                        color = Ink.text,
                    )
                }

                Box(
                    Modifier
                        .padding(start = 6.dp)
                        .background(Ink.amberDark)
                        .padding(2.dp)
                        .background(if (state.showHeat) Ink.amber else Ink.muted)
                        .clickable { model.toggleHeat() }
                        .padding(horizontal = 9.dp, vertical = 9.dp),
                ) {
                    Text("HEAT", style = PixelType.small, color = Ink.amberInk)
                }
            }

            // ---- bottom buttons ----------------------------------------------
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

/** Everything layered over the map: legend, callout, compass, detail card. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MapFurniture(
    state: UiState,
    model: SpideyViewModel,
) {
    val now = state.clock
    var hot = 0
    var warm = 0
    for (sighting in state.live) {
        when (SpideyCore.heatOf(sighting, now)) {
            SpideyCore.Heat.HOT -> hot++
            SpideyCore.Heat.WARM -> warm++
            else -> Unit
        }
    }

    // "Unexplored" is the honest count here: sightings you have not voted on.
    val me = state.profile?.id
    val unexplored = state.live.count { sighting ->
        me == null || (sighting.confirms.none { it.userId == me } &&
            sighting.denies.none { it.userId == me })
    }

    Column(
        Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendBadge(Ink.pinGreen, Ink.pinGreenDark, warm)
        LegendBadge(Ink.pinRed, Ink.pinRedDark, hot)
    }

    if (unexplored > 0 && state.selectedId == null) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .background(Ink.amberDark)
                .padding(2.dp)
                .background(Ink.amber)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                "$unexplored UNEXPLORED\nSIGHTINGS",
                style = PixelType.small,
                color = Ink.amberInk,
                textAlign = TextAlign.Center,
            )
        }
    }

    WebCompass(
        size = 84.dp,
        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
    )

    state.selected?.let { sighting ->
        SightingCard(
            sighting = sighting,
            state = state,
            model = model,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp, start = 8.dp, end = 8.dp),
        )
    }
}

@Composable
private fun OsmMap(state: UiState, model: SpideyViewModel) {
    val context = LocalContext.current
    val overlay = remember { SightingOverlay() }

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
            // Recolours the standard basemap to the deep navy the film's version
            // uses: crush saturation, then push everything into the blues.
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
        onDispose { mapView.onPause() }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            overlay.update(
                sightings = state.live,
                now = state.clock,
                selectedId = state.selectedId,
                myId = state.profile?.id,
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

/** The pin readout, styled as a pixel card like the film's "view sighting" popup. */
@Composable
private fun SightingCard(
    sighting: SpideyCore.Sighting,
    state: UiState,
    model: SpideyViewModel,
    modifier: Modifier = Modifier,
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
        modifier
            .fillMaxWidth()
            .background(Ink.bezelLight)
            .padding(2.dp)
            .background(Ink.navyDeep)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(heatColor(heat))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
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
            Text(meta.label.uppercase(), style = PixelType.small, color = Ink.text)
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
                color = Ink.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            "@${sighting.reporterHandle} · ${SpideyCore.formatDistance(away)} away",
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
            modifier = Modifier.padding(top = 6.dp),
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
