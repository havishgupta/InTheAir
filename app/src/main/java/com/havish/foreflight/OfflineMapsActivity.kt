package com.havish.foreflight

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        mapsListContainer = findViewById(R.id.mapsListContainer)
        tvEmpty = findViewById(R.id.tvEmpty)

        // Add Map button in header
        findViewById<ImageView>(R.id.btnLoadMapFile).setOnClickListener {
            mapFilePicker.launch(arrayOf("*/*"))
        }

        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val switchOfflineOnly = findViewById<android.widget.Switch>(R.id.switchOfflineOnly)
        switchOfflineOnly.isChecked = prefs.getBoolean("offline_maps_only", false)
        switchOfflineOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("offline_maps_only", isChecked).apply()
        }

        // Collapsible FAQ section
        setupFaqSection()

        loadOfflineMaps()
    }

    private fun setupFaqSection() {
        val tvFaqToggle = findViewById<TextView>(R.id.tvFaqToggle)
        val cvInstructions = findViewById<CardView>(R.id.cvInstructions)
        var isExpanded = false

        tvFaqToggle.setOnClickListener {
            isExpanded = !isExpanded
            if (isExpanded) {
                cvInstructions.visibility = View.VISIBLE
                tvFaqToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_up_float, 0)
            } else {
                cvInstructions.visibility = View.GONE
                tvFaqToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0)
            }
        }

        // Make "OpenAndroMaps" a hyperlink in the instruction text
        val tvSteps = findViewById<TextView>(R.id.tvInstructionSteps)
        val fullText = tvSteps.text.toString()
        val linkText = "OpenAndroMaps"
        val startIndex = fullText.indexOf(linkText)

        if (startIndex >= 0) {
            val spannable = SpannableString(fullText)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openandromaps.org/en/downloads/countrys-and-regions"))
                    startActivity(intent)
                }
            }, startIndex, startIndex + linkText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvSteps.text = spannable
            tvSteps.movementMethod = LinkMovementMethod.getInstance()
        }
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
            val btnActive = itemView.findViewById<android.widget.Button>(R.id.btnActive)
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
