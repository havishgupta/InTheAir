package com.havish.foreflight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup OSMDroid config
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = "ForeflightClone/1.0"

        setContentView(R.layout.activity_main)

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(10.0)
        
        // Default location to Mumbai if no GPS initially
        map.controller.setCenter(GeoPoint(19.0760, 72.8777))

        setupLocationOverlay()
        setupUI()
        requestPermissions()
    }

    private fun setupLocationOverlay() {
        val locationProvider = GpsMyLocationProvider(this)
        locationProvider.locationUpdateMinTime = 1000 // 1 second updates
        locationProvider.locationUpdateMinDistance = 1.0f

        locationOverlay = MyLocationNewOverlay(locationProvider, map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
    }

    private fun setupUI() {
        findViewById<FloatingActionButton>(R.id.fabLocation).setOnClickListener {
            if (locationOverlay.isMyLocationEnabled && locationOverlay.myLocation != null) {
                map.controller.animateTo(locationOverlay.myLocation)
                map.controller.setZoom(14.0)
            } else {
                Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnDownload).setOnClickListener {
            val fromCity = findViewById<EditText>(R.id.etFrom).text.toString()
            val toCity = findViewById<EditText>(R.id.etTo).text.toString()
            val tvStatus = findViewById<TextView>(R.id.tvStatus)

            if (fromCity.isBlank() || toCity.isBlank()) {
                Toast.makeText(this, "Enter both From and To locations", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Geocoding locations..."

            scope.launch {
                try {
                    val fromCoord = geocodeCity(fromCity)
                    val toCoord = geocodeCity(toCity)

                    if (fromCoord != null && toCoord != null) {
                        tvStatus.text = "Found coordinates. Calculating bounding box..."
                        
                        // Calculate bounding box encompassing both points
                        val minLat = minOf(fromCoord.latitude, toCoord.latitude) - 0.5
                        val maxLat = maxOf(fromCoord.latitude, toCoord.latitude) + 0.5
                        val minLon = minOf(fromCoord.longitude, toCoord.longitude) - 0.5
                        val maxLon = maxOf(fromCoord.longitude, toCoord.longitude) + 0.5

                        val boundingBox = BoundingBox(maxLat, maxLon, minLat, minLon)
                        
                        // Center map to see the bounding box roughly
                        map.zoomToBoundingBox(boundingBox, true)

                        tvStatus.text = "Downloading offline map (zoom 5-11)..."
                        downloadMapArea(boundingBox, tvStatus)
                    } else {
                        tvStatus.text = "Failed to find one or both locations."
                    }
                } catch (e: Exception) {
                    tvStatus.text = "Error: ${e.message}"
                }
            }
        }
    }

    private suspend fun geocodeCity(city: String): GeoPoint? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search?q=${city.replace(" ", "+")}&format=json&limit=1"
        val request = Request.Builder().url(url).header("User-Agent", "ForeflightClone").build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrEmpty()) {
                    val jsonArray = JSONArray(jsonStr)
                    if (jsonArray.length() > 0) {
                        val obj = jsonArray.getJSONObject(0)
                        val lat = obj.getString("lat").toDouble()
                        val lon = obj.getString("lon").toDouble()
                        return@withContext GeoPoint(lat, lon)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun downloadMapArea(boundingBox: BoundingBox, tvStatus: TextView) {
        val cacheManager = CacheManager(map)
        
        // Warning: Downloading large areas can take a long time and use a lot of storage.
        // For a prototype, zoom levels 5 to 11 are reasonable for a route.
        val zoomMin = 5
        val zoomMax = 11

        cacheManager.downloadAreaAsync(this, boundingBox, zoomMin, zoomMax, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                runOnUiThread {
                    tvStatus.text = "Download Complete!"
                    Toast.makeText(this@MainActivity, "Offline map ready", Toast.LENGTH_LONG).show()
                }
            }

            override fun onTaskFailed(errors: Int) {
                runOnUiThread {
                    tvStatus.text = "Download Failed with $errors errors."
                }
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                runOnUiThread {
                    tvStatus.text = "Downloading... $progress / ${cacheManager.possibleTilesInArea(boundingBox, zoomMin, zoomMax)} tiles"
                }
            }

            override fun downloadStarted() {
                runOnUiThread {
                    tvStatus.text = "Download Started..."
                }
            }

            override fun setPossibleTilesInArea(total: Int) {
                // Not strictly needed since updateProgress uses total
            }
        })
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            // If location was granted, enable it
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationOverlay.enableMyLocation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        locationOverlay.enableMyLocation()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationOverlay.disableMyLocation()
    }
}
