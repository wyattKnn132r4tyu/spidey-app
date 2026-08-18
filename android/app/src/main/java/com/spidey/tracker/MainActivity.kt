package com.spidey.tracker

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The whole app. Map, feed and patrol all live here — nothing opens a browser,
 * and there is no web view anywhere in the build.
 */
class MainActivity : ComponentActivity() {

    private lateinit var permissionLauncher:
        androidx.activity.result.ActivityResultLauncher<Array<String>>
    private lateinit var cameraLauncher:
        androidx.activity.result.ActivityResultLauncher<Void?>
    private var viewModel: SpideyViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { granted ->
            if (granted.values.any { it }) {
                viewModel?.onLocationGranted()
                refreshWidgets()
            }
        }

        // ACTION_IMAGE_CAPTURE via TakePicturePreview needs no CAMERA permission,
        // because the app never declares one — the camera app takes the shot and
        // hands back a thumbnail.
        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicturePreview(),
        ) { bitmap -> if (bitmap != null) viewModel?.setPendingPhoto(bitmap) }

        SpideySense(this).ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
        }

        setContent {
            SpideyTheme {
                val model: SpideyViewModel = viewModel()
                viewModel = model
                val state by model.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    model.start(hasLocation())
                    model.onForeground()
                    if (!hasLocation()) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Ink.bezel)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    if (!state.ready) BootScreen() else SpideyApp(state, model, ::takePhoto)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel?.onForeground()
    }

    override fun onPause() {
        super.onPause()
        // Stops the location watch and the decay clock: patrols are
        // foreground-only, and a timer running behind the app is pure drain.
        viewModel?.onBackground()
        // The home screen should reflect what the app knows.
        refreshWidgets()
    }

    private fun takePhoto() {
        runCatching { cameraLauncher.launch(null) }
    }

    private var notificationLauncher:
        androidx.activity.result.ActivityResultLauncher<String>? = null

    /** Asked for once, the first time the user turns spidey-sense on. */
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun refreshWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, SpideyWidgetProvider::class.java))
        if (ids.isEmpty()) return

        sendBroadcast(
            Intent(this, SpideyWidgetProvider::class.java).apply {
                action = SpideyWidgetProvider.ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            },
        )
    }
}

@Composable
private fun BootScreen() {
    Box(Modifier.fillMaxSize().background(Ink.navyDeep), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PixelSpider(size = 48.dp, body = Ink.pinRed, legs = Ink.bezelLight)
            Text(
                "SCANNING THE CITY",
                style = PixelType.body,
                color = Ink.text,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                "STAND BY",
                style = PixelType.small,
                color = Ink.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SpideyApp(state: UiState, model: SpideyViewModel, onPhoto: () -> Unit) {
    when (state.tab) {
        Tab.MAP -> MapScreen(state, model, onPhoto)
        Tab.BUGLE -> SubScreen("THE BUGLE", model) { BugleScreen(state, model) }
        Tab.PATROL -> SubScreen("PATROL", model) { PatrolScreen(state, model) }
    }
}

/** Bugle and Patrol sit inside the same bezel, with a way back to the map. */
@Composable
private fun SubScreen(title: String, model: SpideyViewModel, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink.bezel).padding(6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AmberButton("< Map") { model.setTab(Tab.MAP) }

            Box(
                Modifier
                    .background(Ink.bezelLight)
                    .padding(2.dp)
                    .background(Ink.navyDeep)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(title, style = PixelType.label, color = Ink.text)
            }

            Box(Modifier.padding(end = 4.dp)) {
                PixelSpider(size = 18.dp, body = Ink.pinRed, legs = Ink.navyDeep)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Ink.bezelDark)
                .padding(2.dp)
                .background(Ink.navyDeep),
        ) {
            content()
        }
    }
}
