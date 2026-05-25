package com.havish.foreflight

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var routeManager: RouteManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        routeManager = RouteManager(this)

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
            startActivity(Intent(this, OfflineMapsActivity::class.java))
        }

        // Routes Section Setup
        loadRecentRoutes()

        findViewById<Button>(R.id.btnViewAllRoutes).setOnClickListener {
            showRoutesListDialog()
        }
    }

    private fun loadRecentRoutes() {
        val llRecentRoutes = findViewById<LinearLayout>(R.id.llRecentRoutes)
        llRecentRoutes.removeAllViews()

        val allRoutes = routeManager.getSavedRoutes()
        if (allRoutes.isEmpty()) {
            val emptyTv = TextView(this)
            emptyTv.text = "No recent routes found."
            emptyTv.setTextColor(android.graphics.Color.parseColor("#555555"))
            llRecentRoutes.addView(emptyTv)
            return
        }

        val recent = allRoutes.take(3)
        for (route in recent) {
            val routeTv = TextView(this)
            routeTv.text = "✈️ ${route.name}"
            routeTv.textSize = 16f
            routeTv.setTextColor(android.graphics.Color.parseColor("#191970"))
            routeTv.setPadding(0, 8, 0, 8)
            routeTv.setOnClickListener {
                showRouteOptionsDialog(route)
            }
            llRecentRoutes.addView(routeTv)
        }
    }

    private fun showRoutesListDialog() {
        val routes = routeManager.getSavedRoutes()
        if (routes.isEmpty()) {
            Toast.makeText(this, "No saved routes", Toast.LENGTH_SHORT).show()
            return
        }
        
        val names = routes.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Saved Routes")
            .setItems(names) { _, which ->
                val selected = routes[which]
                showRouteOptionsDialog(selected)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRouteOptionsDialog(route: RouteData) {
        val options = arrayOf("View on Map", "Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle(route.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("view_route_id", route.id)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
                    1 -> showRenameRouteDialog(route)
                    2 -> {
                        routeManager.deleteRoute(route.id)
                        loadRecentRoutes()
                        Toast.makeText(this, "Deleted ${route.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameRouteDialog(route: RouteData) {
        val input = android.widget.EditText(this)
        input.setText(route.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Route")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    routeManager.renameRoute(route.id, newName)
                    loadRecentRoutes()
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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