package com.havish.foreflight

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private lateinit var tvSpeed: TextView
    private lateinit var tvAlt: TextView
    private lateinit var tvHeading: TextView
    private lateinit var tvClimb: TextView
    private lateinit var tvClimbAngle: TextView

    private var lastAlt = 0.0
    private var lastTime = 0L
    private var lastLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup OSMDroid config to use internal storage to avoid crashes on Android 10+
        val ctx = applicationContext
        val basePath = File(ctx.filesDir, "osmdroid")
        basePath.mkdirs()
        val tileCache = File(basePath, "tiles")
        tileCache.mkdirs()

        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = "InTheAir/1.0"
        Configuration.getInstance().osmdroidBasePath = basePath
        Configuration.getInstance().osmdroidTileCache = tileCache

        setContentView(R.layout.activity_main)

        tvSpeed = findViewById(R.id.tvSpeed)
        tvAlt = findViewById(R.id.tvAlt)
        tvHeading = findViewById(R.id.tvHeading)
        tvClimb = findViewById(R.id.tvClimb)
        tvClimbAngle = findViewById(R.id.tvClimbAngle)

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

    private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)!!
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth * 2, // Slightly larger for map visibility
            drawable.intrinsicHeight * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun setupLocationOverlay() {
        val locationProvider = GpsMyLocationProvider(this)
        locationProvider.locationUpdateMinTime = 1000 // 1 second updates
        locationProvider.locationUpdateMinDistance = 1.0f

        locationOverlay = MyLocationNewOverlay(locationProvider, map)
        
        // Set custom plane icon
        val planeBitmap = getBitmapFromVectorDrawable(this, R.drawable.ic_plane)
        locationOverlay.setPersonIcon(planeBitmap)
        locationOverlay.setDirectionIcon(planeBitmap)
        
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

        findViewById<FloatingActionButton>(R.id.fabSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fabRoute).setOnClickListener {
            showRoutePlanDialog()
        }
    }
    
    private fun setupAutoComplete(editText: AutoCompleteTextView) {
        val geocoder = Geocoder(this@MainActivity)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        editText.setAdapter(adapter)

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.length > 2) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val addresses = geocoder.getFromLocationName(s.toString(), 5)
                            if (!addresses.isNullOrEmpty()) {
                                val results = addresses.mapNotNull {
                                    val name = it.featureName ?: ""
                                    val locality = it.locality ?: ""
                                    val adminArea = it.adminArea ?: ""
                                    val country = it.countryName ?: ""
                                    listOf(name, locality, adminArea, country)
                                        .filter { part -> part.isNotBlank() }
                                        .distinct()
                                        .joinToString(", ")
                                }.distinct()
                                
                                withContext(Dispatchers.Main) {
                                    adapter.clear()
                                    adapter.addAll(results)
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    
    private fun showRoutePlanDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_route_plan, null)
        val etFrom = view.findViewById<AutoCompleteTextView>(R.id.etFrom)
        val etTo = view.findViewById<AutoCompleteTextView>(R.id.etTo)
        val btnDownload = view.findViewById<Button>(R.id.btnDownload)
        val btnDownloadManual = view.findViewById<Button>(R.id.btnDownloadManual)

        setupAutoComplete(etFrom)
        setupAutoComplete(etTo)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnDownloadManual.setOnClickListener {
            dialog.dismiss()
            val boundingBox = map.boundingBox
            downloadMapArea(boundingBox)
        }

        btnDownload.setOnClickListener {
            val fromCity = etFrom.text.toString().trim()
            val toCity = etTo.text.toString().trim()

            if (fromCity.isBlank() || toCity.isBlank()) {
                Toast.makeText(this, "Enter both From and To locations", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            dialog.dismiss()
            Toast.makeText(this, "Calculating route & downloading...", Toast.LENGTH_LONG).show()

            scope.launch {
                try {
                    val fromCoord = geocodeCity(fromCity)
                    val toCoord = geocodeCity(toCity)

                    if (fromCoord != null && toCoord != null) {
                        val minLat = minOf(fromCoord.latitude, toCoord.latitude) - 0.5
                        val maxLat = maxOf(fromCoord.latitude, toCoord.latitude) + 0.5
                        val minLon = minOf(fromCoord.longitude, toCoord.longitude) - 0.5
                        val maxLon = maxOf(fromCoord.longitude, toCoord.longitude) + 0.5

                        val boundingBox = BoundingBox(maxLat, maxLon, minLat, minLon)
                        map.zoomToBoundingBox(boundingBox, true)

                        downloadMapArea(boundingBox)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to find one or both locations.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        dialog.show()
    }

    private suspend fun geocodeCity(city: String): GeoPoint? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(this@MainActivity)
            val addresses = geocoder.getFromLocationName(city, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                return@withContext GeoPoint(address.latitude, address.longitude)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun downloadMapArea(boundingBox: BoundingBox) {
        val cacheManager = CacheManager(map)
        val zoomMin = 5
        val zoomMax = 11

        cacheManager.downloadAreaAsyncNoUI(this, boundingBox, zoomMin, zoomMax, object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Offline map download complete!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onTaskFailed(errors: Int) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Download failed with $errors errors.", Toast.LENGTH_LONG).show()
                }
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                // Background download progress
            }

            override fun downloadStarted() {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Download started...", Toast.LENGTH_SHORT).show()
                }
            }

            override fun setPossibleTilesInArea(total: Int) {}
        })
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationOverlay.enableMyLocation()
                startLocationUpdates()
            }
        }
    }
    
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
        }
    }

    override fun onLocationChanged(location: Location) {
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val speedUnit = prefs.getString("unit_speed", "kts")
        val altUnit = prefs.getString("unit_alt", "ft")
        val climbUnit = prefs.getString("unit_climb", "fpm")

        var verticalSpeedMs = 0.0

        // Speed (m/s to kts, km/h, mph)
        if (location.hasSpeed()) {
            when (speedUnit) {
                "kmh" -> {
                    val speedKmh = location.speed * 3.6
                    tvSpeed.text = String.format("%.0f km/h", speedKmh)
                }
                "mph" -> {
                    val speedMph = location.speed * 2.23694
                    tvSpeed.text = String.format("%.0f mph", speedMph)
                }
                else -> {
                    val speedKts = location.speed * 1.94384
                    tvSpeed.text = String.format("%.0f kts", speedKts)
                }
            }
        }

        // Altitude (meters to feet or meters)
        if (location.hasAltitude()) {
            if (altUnit == "m") {
                tvAlt.text = String.format("%.0f m", location.altitude)
            } else {
                val altFt = location.altitude * 3.28084
                tvAlt.text = String.format("%.0f ft", altFt)
            }
            
            // Climb Calculation
            val currentTime = System.currentTimeMillis()
            if (lastTime > 0) {
                val timeDiffMin = (currentTime - lastTime) / 60000.0
                val timeDiffSec = (currentTime - lastTime) / 1000.0
                if (timeDiffMin > 0 && timeDiffSec > 0) {
                    val altDiff = location.altitude - lastAlt
                    verticalSpeedMs = altDiff / timeDiffSec
                    
                    if (climbUnit == "ms") {
                        tvClimb.text = String.format("%+d m/s", verticalSpeedMs.toInt())
                    } else {
                        val climbFpm = (altDiff * 3.28084) / timeDiffMin
                        tvClimb.text = String.format("%+d fpm", climbFpm.toInt())
                    }
                }
            }
            lastAlt = location.altitude
            lastTime = currentTime
        }

        // Climb Angle Calculation
        if (location.hasSpeed() && location.speed > 0.5) { // Minimum speed to avoid erratic angles
            val angle = Math.toDegrees(atan2(verticalSpeedMs, location.speed.toDouble()))
            tvClimbAngle.text = String.format("%+d°", angle.roundToInt())
        } else {
            tvClimbAngle.text = "0°"
        }

        // Heading
        if (location.hasBearing()) {
            tvHeading.text = String.format("%03d°", location.bearing.toInt())
        } else if (lastLocation != null) {
            val distance = lastLocation!!.distanceTo(location)
            if (distance > 0.5f) { // Only update bearing if moved significantly
                var bearing = lastLocation!!.bearingTo(location)
                if (bearing < 0) bearing += 360f
                tvHeading.text = String.format("%03d°", bearing.toInt())
            }
        }
        lastLocation = location
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        locationOverlay.enableMyLocation()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationOverlay.disableMyLocation()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(this)
    }
}