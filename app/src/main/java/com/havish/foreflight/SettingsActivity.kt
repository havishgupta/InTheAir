package com.havish.foreflight

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

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

        findViewById<Button>(R.id.btnViewOffline).setOnClickListener {
            startActivity(Intent(this, OfflineMapsActivity::class.java))
        }

        // Routes Section Setup
        loadRecentRoutes()

        findViewById<Button>(R.id.btnViewAllRoutes).setOnClickListener {
            showRoutesBottomSheet()
        }
    }

    private fun loadRecentRoutes() {
        val llRecentRoutes = findViewById<LinearLayout>(R.id.llRecentRoutes)
        llRecentRoutes.removeAllViews()

        val allRoutes = routeManager.getSavedRoutes()
        if (allRoutes.isEmpty()) {
            val emptyTv = TextView(this)
            emptyTv.text = "No recent routes found."
            emptyTv.setTextColor(getColor(R.color.text_dark_grey))
            llRecentRoutes.addView(emptyTv)
            return
        }

        val recent = allRoutes.take(3)
        for (route in recent) {
            val routeTv = TextView(this)
            routeTv.text = "✈️ ${route.name}"
            routeTv.textSize = 16f
            routeTv.setTextColor(getColor(R.color.text_dark_grey))
            routeTv.setPadding(0, 8, 0, 8)
            routeTv.setOnClickListener {
                showRoutesBottomSheet() // Just open the main sheet
            }
            llRecentRoutes.addView(routeTv)
        }
    }

    private fun showRoutesBottomSheet() {
        val routes = routeManager.getSavedRoutes()
        if (routes.isEmpty()) {
            Toast.makeText(this, "No saved routes", Toast.LENGTH_SHORT).show()
            return
        }

        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_bottom_sheet_routes, null)
        bottomSheetDialog.setContentView(view)

        val rvRoutes = view.findViewById<RecyclerView>(R.id.rvRoutes)
        rvRoutes.layoutManager = LinearLayoutManager(this)
        rvRoutes.adapter = RouteAdapter(routes.toMutableList(), bottomSheetDialog)

        val btnClose = view.findViewById<Button>(R.id.btnClose)
        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private inner class RouteAdapter(private val routeList: MutableList<RouteData>, private val dialog: BottomSheetDialog) : RecyclerView.Adapter<RouteAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvRouteName: TextView = itemView.findViewById(R.id.tvRouteName)
            val ivView: ImageView = itemView.findViewById(R.id.ivView)
            val ivRename: ImageView = itemView.findViewById(R.id.ivRename)
            val ivDelete: ImageView = itemView.findViewById(R.id.ivDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_route_bottom_sheet, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val route = routeList[position]
            holder.tvRouteName.text = route.name

            holder.ivView.setOnClickListener {
                dialog.dismiss()
                val intent = Intent(this@SettingsActivity, MainActivity::class.java)
                intent.putExtra("view_route_id", route.id)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                finish()
            }

            holder.ivRename.setOnClickListener {
                showRenameRouteDialog(route, position)
            }

            holder.ivDelete.setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Delete Route")
                    .setMessage("Are you sure you want to delete ${route.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        routeManager.deleteRoute(route.id)
                        routeList.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, routeList.size)
                        loadRecentRoutes()
                        Toast.makeText(this@SettingsActivity, "Deleted", Toast.LENGTH_SHORT).show()
                        if (routeList.isEmpty()) dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount(): Int = routeList.size

        private fun showRenameRouteDialog(route: RouteData, position: Int) {
            val input = android.widget.EditText(this@SettingsActivity)
            input.setText(route.name)
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Rename Route")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString()
                    if (newName.isNotBlank()) {
                        routeManager.renameRoute(route.id, newName)
                        route.name = newName
                        notifyItemChanged(position)
                        loadRecentRoutes()
                        Toast.makeText(this@SettingsActivity, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}