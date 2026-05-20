package com.havish.foreflight

import android.content.Context
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

        // Altitude
        when (prefs.getString("unit_alt", "m")) {
            "ft" -> findViewById<RadioButton>(R.id.rbAltFt).isChecked = true
            "m" -> findViewById<RadioButton>(R.id.rbAltM).isChecked = true
            "km" -> findViewById<RadioButton>(R.id.rbAltKm).isChecked = true
            "bk" -> findViewById<RadioButton>(R.id.rbAltBk).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgAltUnits).setOnCheckedChangeListener { _, checkedId ->
            val unit = when (checkedId) {
                R.id.rbAltM -> "m"
                R.id.rbAltKm -> "km"
                R.id.rbAltBk -> "bk"
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
                Toast.makeText(this, "Offline Tile Cache Cleared", Toast.LENGTH_SHORT).show()
                updateCacheSize()
            }
        }

        findViewById<Button>(R.id.btnViewOffline).setOnClickListener {
            startActivity(android.content.Intent(this, OfflineMapsActivity::class.java))
        }
    }

    private fun updateCacheSize() {
        val tvCacheSize = findViewById<TextView>(R.id.tvCacheSize)
        val tileCache = Configuration.getInstance().osmdroidTileCache
        if (tileCache != null && tileCache.exists()) {
            val sizeBytes = getFolderSize(tileCache)
            val sizeMb = sizeBytes / (1024.0 * 1024.0)
            tvCacheSize.text = String.format("Current Cache Size: %.2f MB", sizeMb)
        } else {
            tvCacheSize.text = "Current Cache Size: 0 MB"
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