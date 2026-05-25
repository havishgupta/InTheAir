package com.havish.foreflight

import android.content.Context
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OfflineMapsActivity : AppCompatActivity() {

    private lateinit var mapsListContainer: LinearLayout
    private lateinit var tvEmpty: TextView
    private val scope = CoroutineScope(Dispatchers.Main)

    private val mapFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importAndLoadMapFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_offline_maps)

        mapsListContainer = findViewById(R.id.mapsListContainer)
        tvEmpty = findViewById(R.id.tvEmpty)

        findViewById<Button>(R.id.btnLoadMapFile).setOnClickListener {
            mapFilePicker.launch(arrayOf("*/*"))
        }

        loadOfflineMaps()
    }

    private fun getFileName(uri: android.net.Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "offline_map_${System.currentTimeMillis()}.map"
    }

    private fun importAndLoadMapFile(uri: android.net.Uri) {
        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Importing Map")
            .setMessage("Please wait while the map file is copied to the app's secure storage...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        val fileName = getFileName(uri)

        scope.launch {
            try {
                val mapsDir = File(filesDir, "mapsforge")
                mapsDir.mkdirs()
                
                val destFile = File(mapsDir, fileName)

                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("active_offline_map", fileName).apply()

                progressDialog.dismiss()
                Toast.makeText(this@OfflineMapsActivity, "Map imported successfully!", Toast.LENGTH_SHORT).show()
                loadOfflineMaps()
                
            } catch (e: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@OfflineMapsActivity, "Failed to import map: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
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
                btnActive.text = "Active"
                btnActive.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
                btnActive.isEnabled = false
            } else {
                btnActive.text = "Set Active"
                btnActive.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#87CEEB")) // Sky Blue
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
