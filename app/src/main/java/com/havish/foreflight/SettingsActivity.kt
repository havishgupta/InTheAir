package com.havish.foreflight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)

        // Offline Maps Only
        val switchOffline = findViewById<android.widget.Switch>(R.id.switchOfflineOnly)
        switchOffline.isChecked = prefs.getBoolean("offline_maps_only", false)
        switchOffline.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("offline_maps_only", isChecked).apply()
        }

        // Action: Route Download
        findViewById<Button>(R.id.btnDownloadRouteMap).setOnClickListener {
            val intent = Intent().apply {
                putExtra("action", "show_route_dialog")
            }
            setResult(RESULT_OK, intent)
            finish()
        }

        // Speed
        when (prefs.getString("unit_speed", "kmh")) {
            "kts" -> findViewById<RadioButton>(R.id.rbSpeedKts).isChecked = true
            "kmh" -> findViewById<RadioButton>(R.id.rbSpeedKmh).isChecked = true
            "mph" -> findViewById<RadioButton>(R.id.rbSpeedMph).isChecked = true
            "mach" -> findViewById<RadioButton>(R.id.rbSpeedMach).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgSpeedUnits).setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                R.id.rbSpeedKmh -> "kmh"
                R.id.rbSpeedMph -> "mph"
                R.id.rbSpeedMach -> "mach"
                else -> "kts"
            }
            prefs.edit().putString("unit_speed", unit).apply()
        }

        findViewById<RadioButton>(R.id.rbSpeedMach).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) prefs.edit().putString("unit_speed", "mach").apply()
        }

        // Initial Zoom
        when (prefs.getFloat("initial_zoom", 14.0f)) {
            10.0f -> findViewById<RadioButton>(R.id.rbZoomFar).isChecked = true
            18.0f -> findViewById<RadioButton>(R.id.rbZoomClose).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbZoomMedium).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgInitialZoom).setOnCheckedChangeListener { _, checkedId ->
            val zoom = when (checkedId) {
                R.id.rbZoomFar -> 10.0f
                R.id.rbZoomClose -> 18.0f
                else -> 14.0f
            }
            prefs.edit().putFloat("initial_zoom", zoom).apply()
        }

        // Debug Mode
        val switchDebug = findViewById<android.widget.Switch>(R.id.switchDebugMode)
        switchDebug.isChecked = prefs.getBoolean("debug_mode", false)
        switchDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debug_mode", isChecked).apply()
        }

        // Altitude
        when (prefs.getString("unit_alt", "m")) {
            "ft" -> findViewById<RadioButton>(R.id.rbAltFt).isChecked = true
            "m" -> findViewById<RadioButton>(R.id.rbAltM).isChecked = true
            "km" -> findViewById<RadioButton>(R.id.rbAltKm).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgAltUnits).setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                R.id.rbAltM -> "m"
                R.id.rbAltKm -> "km"
                else -> "ft"
            }
            prefs.edit().putString("unit_alt", unit).apply()
        }

        // Climb Rate
        when (prefs.getString("unit_climb", "ms")) {
            "fpm" -> findViewById<RadioButton>(R.id.rbClimbFpm).isChecked = true
            "ms" -> findViewById<RadioButton>(R.id.rbClimbMs).isChecked = true
            "kmh" -> findViewById<RadioButton>(R.id.rbClimbKmh).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgClimbUnits).setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                R.id.rbClimbMs -> "ms"
                R.id.rbClimbKmh -> "kmh"
                else -> "fpm"
            }
            prefs.edit().putString("unit_climb", unit).apply()
        }

        updateCacheSize()

        findViewById<Button>(R.id.btnClearCache).setOnClickListener {
            val tileCache = Configuration.getInstance().osmdroidTileCache
            if (tileCache != null && tileCache.exists()) {
                tileCache.deleteRecursively()
                tileCache.mkdirs()
                Toast.makeText(this, "Offline Tile Cache Purged", Toast.LENGTH_SHORT).show()
                updateCacheSize()
            }
        }

        findViewById<Button>(R.id.btnViewOffline).setOnClickListener {
            startActivity(Intent(this, OfflineMapsActivity::class.java))
        }
    }

    private fun updateCacheSize() {
        val tvCacheSize = findViewById<TextView>(R.id.tvCacheSize)
        val tileCache = Configuration.getInstance().osmdroidTileCache
        if (tileCache != null && tileCache.exists()) {
            val sizeBytes = getFolderSize(tileCache)
            val sizeMb = sizeBytes / (1024.0 * 1024.0)
            tvCacheSize.text = String.format("Cache: %.2f MB", sizeMb)
        } else {
            tvCacheSize.text = "Cache: 0.00 MB"
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size = file.length()
        }
        return size
    }
}