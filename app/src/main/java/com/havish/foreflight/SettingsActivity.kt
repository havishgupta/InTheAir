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
    }
}