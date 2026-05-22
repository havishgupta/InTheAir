package com.havish.foreflight

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class OfflineMapsActivity : AppCompatActivity() {

    private lateinit var mapsListContainer: LinearLayout
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_maps)

        mapsListContainer = findViewById(R.id.mapsListContainer)
        tvEmpty = findViewById(R.id.tvEmpty)

        loadOfflineMaps()
    }

    private fun loadOfflineMaps() {
        mapsListContainer.removeAllViews()
        val mapsDir = File(filesDir, "mapsforge")
        val mapFiles = mapsDir.listFiles()?.filter { it.extension == "map" } ?: emptyList()

        if (mapFiles.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            return
        }

        tvEmpty.visibility = View.GONE
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val activeMap = prefs.getString("active_offline_map", null)

        for (file in mapFiles) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_offline_map, mapsListContainer, false)
            
            val tvMapName = itemView.findViewById<TextView>(R.id.tvMapName)
            val tvMapSize = itemView.findViewById<TextView>(R.id.tvMapSize)
            val btnActive = itemView.findViewById<Button>(R.id.btnActive)
            val btnDelete = itemView.findViewById<ImageView>(R.id.btnDelete)

            tvMapName.text = file.name
            
            val sizeMb = file.length() / (1024.0 * 1024.0)
            tvMapSize.text = String.format("%.2f MB", sizeMb)

            if (file.name == activeMap) {
                btnActive.text = "[ ACTIVE ]"
                btnActive.setTextColor(android.graphics.Color.parseColor("#0FFFFF"))
                btnActive.isEnabled = false
            } else {
                btnActive.text = "[ SET ]"
                btnActive.setTextColor(android.graphics.Color.parseColor("#FF00FF"))
                btnActive.isEnabled = true
                btnActive.setOnClickListener {
                    prefs.edit().putString("active_offline_map", file.name).apply()
                    loadOfflineMaps() // refresh list
                    Toast.makeText(this, "${file.name} set as active", Toast.LENGTH_SHORT).show()
                }
            }

            btnDelete.setOnClickListener {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Delete Map")
                    .setMessage("Are you sure you want to delete ${file.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        if (file.delete()) {
                            if (file.name == activeMap) {
                                prefs.edit().remove("active_offline_map").apply()
                            }
                            loadOfflineMaps()
                            Toast.makeText(this, "Map deleted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to delete map", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            mapsListContainer.addView(itemView)
        }
    }
}
