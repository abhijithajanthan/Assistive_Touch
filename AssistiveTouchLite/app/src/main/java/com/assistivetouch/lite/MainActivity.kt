package com.assistivetouch.lite

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Find \"Assistive Touch Lite\" in the list and turn it on",
                Toast.LENGTH_LONG
            ).show()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant the overlay permission first", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
            val intent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Floating button started", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Floating button stopped", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessibilityOk = isAccessibilityServiceEnabled()
        statusText.text = "Overlay permission: ${if (overlayOk) "Granted ✅" else "Not granted ❌"}\n" +
                "Accessibility service: ${if (accessibilityOk) "Enabled ✅" else "Not enabled ❌"}"
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }
}
