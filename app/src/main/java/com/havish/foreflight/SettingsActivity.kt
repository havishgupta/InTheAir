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

        // Customization: Show Scales
        val switchShowScales = findViewById<android.widget.Switch>(R.id.switchShowScales)
        switchShowScales.isChecked = prefs.getBoolean("show_scales_on_home", false)
        switchShowScales.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_scales_on_home", isChecked).apply()
        }

        // Customization: Mode Indicator
        val switchModeIndicator = findViewById<android.widget.Switch>(R.id.switchModeIndicator)
        switchModeIndicator.isChecked = prefs.getBoolean("show_mode_indicator", true)
        switchModeIndicator.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_mode_indicator", isChecked).apply()
        }

        // Customization: Voyage Button
        val switchVoyageBtn = findViewById<android.widget.Switch>(R.id.switchVoyageBtn)
        switchVoyageBtn.isChecked = prefs.getBoolean("show_voyage_btn", true)
        switchVoyageBtn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_voyage_btn", isChecked).apply()
        }

        // Customization: Long Press Note
        val switchLongPressNote = findViewById<android.widget.Switch>(R.id.switchLongPressNote)
        switchLongPressNote.isChecked = prefs.getBoolean("enable_long_press_note", true)
        switchLongPressNote.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("enable_long_press_note", isChecked).apply()
        }

        // Customization: Long Press Duration
        when (prefs.getInt("long_press_duration", 500)) {
            1000 -> findViewById<RadioButton>(R.id.rbLongPress1000).isChecked = true
            1500 -> findViewById<RadioButton>(R.id.rbLongPress1500).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbLongPress500).isChecked = true
        }
        findViewById<RadioGroup>(R.id.rgLongPressDuration).setOnCheckedChangeListener { _, checkedId ->
            val duration = when (checkedId) {
                R.id.rbLongPress1000 -> 1000
                R.id.rbLongPress1500 -> 1500
                else -> 500
            }
            prefs.edit().putInt("long_press_duration", duration).apply()
        }

        // Customization: Telemetry Visibility
        when (prefs.getString("telemetry_visibility", "always")) {
            "voyage" -> findViewById<RadioButton>(R.id.rbTeleVoyage).isChecked = true
            "hidden" -> findViewById<RadioButton>(R.id.rbTeleHidden).isChecked = true
            else -> findViewById<RadioButton>(R.id.rbTeleAlways).isChecked = true
        }
        findViewById<RadioGroup>(R.id.rgTelemetryVis).setOnCheckedChangeListener { _, checkedId ->
            val visibility = when (checkedId) {
                R.id.rbTeleVoyage -> "voyage"
                R.id.rbTeleHidden -> "hidden"
                else -> "always"
            }
            prefs.edit().putString("telemetry_visibility", visibility).apply()
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

        fun convertAlt(meters: Double): String {
            return when (altUnit) {
                "m" -> String.format("%.0fm", meters)
                "km" -> String.format("%.1fkm", meters / 1000.0)
                "bk" -> String.format("%.1fbk", meters / 828.0)
                else -> String.format("%.0fft", meters * 3.28084)
            }
        }

        // 4-stop altitude key: Ground (0%), Low (33%), Mid (66%), High (100%)
        findViewById<android.widget.TextView>(R.id.tvKeyAltGround).text = "0$altUnit"
        findViewById<android.widget.TextView>(R.id.tvKeyAltLow).text = convertAlt(maxAltMeters * 0.33)
        findViewById<android.widget.TextView>(R.id.tvKeyAltMid).text = convertAlt(maxAltMeters * 0.66)
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

}", ms * 1.94384)
            }
        }

        findViewById<android.widget.TextView>(R.id.tvKeySpeedSlow).text = "  0$speedUnit  "
        findViewById<android.widget.TextView>(R.id.tvKeySpeedFast).text = "  ${convertSpeed(maxSpeedMs)}+"
    }

}