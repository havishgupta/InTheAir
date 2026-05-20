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
        val isMetric = prefs.getBoolean("is_metric", false)

        if (isMetric) {
            findViewById<RadioButton>(R.id.rbMetric).isChecked = true
        } else {
            findViewById<RadioButton>(R.id.rbKnots).isChecked = true
        }

        findViewById<RadioGroup>(R.id.rgSpeedUnits).setOnCheckedChangeListener { _, checkedId ->
            prefs.edit().putBoolean("is_metric", checkedId == R.id.rbMetric).apply()
        }

        updateCacheSize()

        findViewById<Button>(R.id.btnClearCache).setOnClickListener {
            val tileCache = Configuration.getInstance().osmdroidTileCache
            if (tileCache != null && tileCache.exists()) {
                tileCache.deleteRecursively()
                tileCache.mkdirs()
                Toast.makeText(this, "Offline Maps Cleared", Toast.LENGTH_SHORT).show()
                updateCacheSize()
            }
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
