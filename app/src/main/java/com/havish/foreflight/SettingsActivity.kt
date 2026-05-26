package com.havish.foreflight

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)

        // Back button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        // Voyage Mode
        when (prefs.getString("voyage_mode", "plane")) {
            "car" -> findViewById<RadioButton>(R.id.rbModeCar).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbModePlane).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgVoyageMode).setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbModeCar -> "car"
                else -> "plane"
            }
            prefs.edit().putString("voyage_mode", mode).apply()
            updateMapKey()
        }

        // Speed Units
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
            updateMapKey()
        }

        // Altitude Units
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
            updateMapKey()
        }

        // Climb Rate Units
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

        // Logging Interval
        when (prefs.getInt("logging_interval", 1)) {
            2 -> findViewById<RadioButton>(R.id.rbLog2s).isChecked = true
            3 -> findViewById<RadioButton>(R.id.rbLog3s).isChecked = true
            4 -> findViewById<RadioButton>(R.id.rbLog4s).isChecked = true
            5 -> findViewById<RadioButton>(R.id.rbLog5s).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbLog1s).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgLoggingInterval).setOnCheckedChangeListener { _, checkedId ->
            val interval = when (checkedId) {
                R.id.rbLog2s -> 2
                R.id.rbLog3s -> 3
                R.id.rbLog4s -> 4
                R.id.rbLog5s -> 5
                else -> 1
            }
            prefs.edit().putInt("logging_interval", interval).apply()
        }

        // Debug Mode
        val switchDebug = findViewById<android.widget.Switch>(R.id.switchDebugMode)
        switchDebug.isChecked = prefs.getBoolean("debug_mode", false)
        switchDebug.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debug_mode", isChecked).apply()
        }

        // Offline Maps Only
        val switchOfflineOnly = findViewById<android.widget.Switch>(R.id.switchOfflineOnly)
        switchOfflineOnly.isChecked = prefs.getBoolean("offline_maps_only", false)
        switchOfflineOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("offline_maps_only", isChecked).apply()
        }

        updateMapKey()
    }

    private fun updateMapKey() {
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("voyage_mode", "plane") ?: "plane"
        val altUnit = prefs.getString("unit_alt", "m") ?: "m"
        val speedUnit = prefs.getString("unit_speed", "kmh") ?: "kmh"

        val maxAltMeters = if (mode == "car") 609.0 else 12000.0
        val maxSpeedMs = if (mode == "car") 55.5 else 300.0

        val midAltMeters = maxAltMeters / 2.0

        fun convertAlt(meters: Double): String {
            return when (altUnit) {
                "m" -> String.format("%.0fm", meters)
                "km" -> String.format("%.1fkm", meters / 1000.0)
                "bk" -> String.format("%.1fbk", meters / 828.0)
                else -> String.format("%.0fft", meters * 3.28084)
            }
        }

        findViewById<android.widget.TextView>(R.id.tvKeyAltLow).text = "0$altUnit"
        findViewById<android.widget.TextView>(R.id.tvKeyAltMid).text = convertAlt(midAltMeters)
        findViewById<android.widget.TextView>(R.id.tvKeyAltHigh).text = convertAlt(maxAltMeters) + "+"

        fun convertSpeed(ms: Double): String {
            return when (speedUnit) {
                "kmh" -> String.format("%.0fkm/h", ms * 3.6)
                "mph" -> String.format("%.0fmph", ms * 2.23694)
                "mach" -> String.format("%.2fM", ms / 343.0)
                else -> String.format("%.0fkts", ms * 1.94384)
            }
        }

        findViewById<android.widget.TextView>(R.id.tvKeySpeedSlow).text = "  0$speedUnit  "
        findViewById<android.widget.TextView>(R.id.tvKeySpeedFast).text = "  ${convertSpeed(maxSpeedMs)}+"
    }
}