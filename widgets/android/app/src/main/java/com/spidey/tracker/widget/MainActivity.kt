package com.spidey.tracker.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * The widget itself cannot request permissions, so this is where location access
 * is granted. It otherwise does one thing: open the web app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)

        findViewById<Button>(R.id.grant).setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
                REQUEST_LOCATION,
            )
        }

        findViewById<Button>(R.id.open).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SpideyWidgetProvider.APP_URL)))
        }

        findViewById<Button>(R.id.refresh).setOnClickListener { refreshWidgets() }
    }

    override fun onResume() {
        super.onResume()
        status.text = if (hasLocation()) {
            "Location granted. The widget updates about every 30 minutes, and " +
                "whenever you tap Refresh."
        } else {
            "Without location the widget falls back to midtown Manhattan."
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION) refreshWidgets()
    }

    private fun hasLocation() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun refreshWidgets() {
        val intent = Intent(this, SpideyWidgetProvider::class.java).apply {
            action = SpideyWidgetProvider.ACTION_REFRESH
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_IDS,
                AppWidgetManager.getInstance(this@MainActivity).getAppWidgetIds(
                    ComponentName(this@MainActivity, SpideyWidgetProvider::class.java),
                ),
            )
        }
        sendBroadcast(intent)
    }

    companion object {
        private const val REQUEST_LOCATION = 1
    }
}
