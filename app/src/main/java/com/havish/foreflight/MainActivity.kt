package com.havish.foreflight

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.mapsforge.MapsForgeTileSource
import org.osmdroid.mapsforge.MapsForgeTileProvider
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.rendertheme.InternalRenderTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private lateinit var tvSpeed: TextView
    private lateinit var tvAlt: TextView
    private lateinit var tvHeading: TextView
    private lateinit var tvClimb: TextView
    private lateinit var tvClimbAngle: TextView

    private lateinit var cardDebugMode: CardView
    private lateinit var tvDebugLatLon: TextView
    private lateinit var tvDebugAcc: TextView
    private lateinit var tvDebugRawSpeed: TextView
    private lateinit var tvDebugRawAlt: TextView
    private lateinit var tvDebugBearing: TextView
    private lateinit var ivCompassArrow: ImageView

    private var lastAlt = 0.0
    private var lastTime = 0L
    private var lastLocation: Location? = null
    private var downloadJob: Job? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var routeManager: RouteManager
    private var isRecordingRoute = false
    private var currentRoute: RouteData? = null
    private var lastLogTime = 0L
    private val routeLines = mutableListOf<org.osmdroid.views.overlay.Polyline>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Mapsforge graphics factory before anything else
        AndroidGraphicFactory.createInstance(application)

        // Setup OSMDroid config to use internal storage
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
        
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val initialZoom = prefs.getFloat("initial_zoom", 14.0f).toDouble()
        map.controller.setZoom(initialZoom)
        // Removed default Mumbai location - waiting for GPS

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()

        setupLocationOverlay()
        setupUI()
        requestPermissions()
        
        // Check for existing loaded map file
        checkForSavedMapFile()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AndroidGraphicFactory.clearResourceMemoryCache()
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
        routeManager = RouteManager(this)

        val fabRecord = findViewById<FloatingActionButton>(R.id.fabRecord)
        fabRecord.setOnClickListener {
            toggleRouteRecording(fabRecord)
        }

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

        cardDebugMode = findViewById(R.id.cardDebugMode)
        tvDebugLatLon = findViewById(R.id.tvDebugLatLon)
        tvDebugAcc = findViewById(R.id.tvDebugAcc)
        tvDebugRawSpeed = findViewById(R.id.tvDebugRawSpeed)
        tvDebugRawAlt = findViewById(R.id.tvDebugRawAlt)
        tvDebugBearing = findViewById(R.id.tvDebugBearing)
        ivCompassArrow = findViewById(R.id.ivCompassArrow)
    }
    
    private fun setupAutoComplete(editText: AutoCompleteTextView) {
        val geocoder = Geocoder(this@MainActivity)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        editText.setAdapter(adapter)
        editText.threshold = 2

        var isUserSelecting = false
        var searchJob: Job? = null

        editText.setOnItemClickListener { parent, _, position, _ ->
            isUserSelecting = true
            val selected = parent.getItemAtPosition(position) as String
            editText.setText(selected)
            editText.setSelection(selected.length)
            editText.dismissDropDown()
            editText.postDelayed({ isUserSelecting = false }, 300)
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isUserSelecting) return

                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    searchJob?.cancel()
                    searchJob = scope.launch {
                        delay(300)
                        try {
                            val results = withContext(Dispatchers.IO) {
                                val addresses = geocoder.getFromLocationName(query, 5)
                                addresses?.mapNotNull {
                                    val name = it.featureName ?: ""
                                    val locality = it.locality ?: ""
                                    val adminArea = it.adminArea ?: ""
                                    val country = it.countryName ?: ""
                                    listOf(name, locality, adminArea, country)
                                        .filter { part -> part.isNotBlank() }
                                        .distinct()
                                        .joinToString(", ")
                                }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
                            }

                            if (results.isNotEmpty()) {
                                adapter.clear()
                                adapter.addAll(results)
                                adapter.notifyDataSetChanged()
                                if (editText.isFocused && !isUserSelecting) {
                                    editText.post {
                                        try {
                                            editText.showDropDown()
                                        } catch (e: Exception) {
                                            Log.w("AutoComplete", "Could not show dropdown: ${e.message}")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AutoComplete", "Geocoding error: ${e.message}", e)
                        }
                    }
                } else {
                    adapter.clear()
                    adapter.notifyDataSetChanged()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && adapter.count > 0 && !isUserSelecting) {
                editText.post {
                    try {
                        editText.showDropDown()
                    } catch (e: Exception) {
                        Log.w("AutoComplete", "Could not show dropdown on focus: ${e.message}")
                    }
                }
            }
        }
    }
    
    private fun showRoutePlanDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_route_plan, null)
        val etFrom = view.findViewById<AutoCompleteTextView>(R.id.etFrom)
        val etTo = view.findViewById<AutoCompleteTextView>(R.id.etTo)
        val btnDownload = view.findViewById<Button>(R.id.btnDownload)
        val btnDownloadManual = view.findViewById<Button>(R.id.btnDownloadManual)
        val btnDownloadIndia = view.findViewById<Button>(R.id.btnDownloadIndia)

        setupAutoComplete(etFrom)
        setupAutoComplete(etTo)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnDownloadManual.setOnClickListener {
            dialog.dismiss()
            try {
                val boundingBox = map.boundingBox
                downloadMapArea(boundingBox)
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnDownloadIndia.setOnClickListener {
            dialog.dismiss()
            downloadIndiaMap()
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

                        delay(500)
                        downloadMapArea(boundingBox)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to find one or both locations. Check internet.", Toast.LENGTH_LONG).show()
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
        try {
            if (boundingBox.latNorth <= boundingBox.latSouth || boundingBox.lonEast <= boundingBox.lonWest) {
                Toast.makeText(this, "Invalid map area. Please try again.", Toast.LENGTH_LONG).show()
                return
            }

            val helper = TileDownloadHelper(map)
            val zoomMin = 5
            val zoomMax = 10

            val tileCount = helper.calculateTiles(boundingBox, zoomMin, zoomMax).size
            val estMinutes = (tileCount * 0.3 / 60).toInt() + 1

            startDownload(helper, boundingBox, zoomMin, zoomMax, "Route Area", tileCount, estMinutes)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadIndiaMap() {
        val indiaBB = BoundingBox(37.0, 98.0, 6.0, 68.0)
        val zoomMin = 3
        val zoomMax = 8

        val helper = TileDownloadHelper(map)
        val tileCount = helper.calculateTiles(indiaBB, zoomMin, zoomMax).size
        val estMinutes = (tileCount * 0.3 / 60).toInt() + 1

        startDownload(helper, indiaBB, zoomMin, zoomMax, "All India", tileCount, estMinutes)
    }

    private fun startDownload(
        helper: TileDownloadHelper,
        boundingBox: BoundingBox,
        zoomMin: Int,
        zoomMax: Int,
        areaName: String,
        tileCount: Int,
        estMinutes: Int
    ) {
        AlertDialog.Builder(this)
            .setTitle("Download $areaName")
            .setMessage(
                "This will download ~$tileCount tiles (zoom $zoomMin-$zoomMax).\n\n" +
                "Estimated time: ~$estMinutes minutes.\n\n" +
                "Make sure you are on Wi-Fi."
            )
            .setPositiveButton("Download") { _, _ ->
                downloadJob?.cancel()

                val progressView = LayoutInflater.from(this).inflate(
                    android.R.layout.simple_list_item_1, null
                )
                val tvProgress = progressView.findViewById<TextView>(android.R.id.text1)
                tvProgress.text = "Preparing download..."
                tvProgress.setPadding(48, 32, 48, 32)

                val progressDialog = AlertDialog.Builder(this)
                    .setTitle("Downloading $areaName")
                    .setView(tvProgress)
                    .setCancelable(false)
                    .setNegativeButton("Cancel") { dlg, _ ->
                        downloadJob?.cancel()
                        dlg.dismiss()
                        Toast.makeText(this, "Download cancelled.", Toast.LENGTH_SHORT).show()
                    }
                    .create()
                progressDialog.show()

                downloadJob = helper.downloadArea(
                    boundingBox, zoomMin, zoomMax,
                    object : TileDownloadHelper.Callback {
                        override fun onStart(totalTiles: Int) {
                            tvProgress.text = "Starting download of $totalTiles tiles..."
                        }

                        override fun onProgress(completed: Int, total: Int, currentZoom: Int) {
                            val pct = if (total > 0) (completed * 100 / total) else 0
                            tvProgress.text = "Downloaded $completed / $total tiles ($pct%)\nZoom level: $currentZoom"
                        }

                        override fun onComplete(downloaded: Int, errors: Int) {
                            if (progressDialog.isShowing) progressDialog.dismiss()
                            if (errors == 0) {
                                Toast.makeText(this@MainActivity, "$areaName download complete!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "$areaName: downloaded, $errors errors.", Toast.LENGTH_LONG).show()
                            }
                        }

                        override fun onError(message: String) {
                            if (progressDialog.isShowing) progressDialog.dismiss()
                            Toast.makeText(this@MainActivity, "Download error: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        val progressDialog = AlertDialog.Builder(this)
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
                loadMapsforgeFile(destFile)
                
            } catch (e: Throwable) {
                progressDialog.dismiss()
                Toast.makeText(this@MainActivity, "Failed to import map: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkForSavedMapFile() {
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val offlineOnly = prefs.getBoolean("offline_maps_only", false)
        val activeMapName = prefs.getString("active_offline_map", null)
        
        cardDebugMode.visibility = if (prefs.getBoolean("debug_mode", false)) View.VISIBLE else View.GONE

        if (!offlineOnly) {
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setUseDataConnection(true)
            return
        }

        val mapsDir = File(filesDir, "mapsforge")
        if (activeMapName != null) {
            val mapFile = File(mapsDir, activeMapName)
            if (mapFile.exists()) {
                loadMapsforgeFile(mapFile)
                return
            }
        }
        
        // fallback to first map if active not found
        val firstMap = mapsDir.listFiles()?.firstOrNull { it.extension == "map" }
        if (firstMap != null) {
            loadMapsforgeFile(firstMap)
        } else {
            Toast.makeText(this, "Offline maps only enabled, but no map found.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadMapsforgeFile(file: File) {
        try {
            MapsForgeTileSource.createInstance(application)
            val theme = InternalRenderTheme.OSMARENDER
            val tileSource = MapsForgeTileSource.createFromFiles(arrayOf(file), theme, "OSMARENDER")
            val tileProvider = MapsForgeTileProvider(
                org.osmdroid.tileprovider.util.SimpleRegisterReceiver(this),
                tileSource,
                null
            )
            
            map.setTileProvider(tileProvider)
            map.setTileSource(tileSource)
            map.setUseDataConnection(false)
            
            val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
            val initialZoom = prefs.getFloat("initial_zoom", 14.0f).toDouble()
            map.controller.setZoom(initialZoom)
            
            // Removed offline map loaded toast
        } catch (e: Throwable) {
            Toast.makeText(this, "Failed to load offline map: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
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
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                              ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation) {
                locationOverlay.enableMyLocation()
                startLocationUpdates()
            }
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processLocation(location)
                }
            }
        }
    }
    
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .build()

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    processLocation(location)
                }
            }
        }
    }

    private fun processLocation(location: Location) {
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val speedUnit = prefs.getString("unit_speed", "kmh")
        val altUnit = prefs.getString("unit_alt", "m")
        val climbUnit = prefs.getString("unit_climb", "ms")

        var verticalSpeedMs = 0.0

        // Speed
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
                "mach" -> {
                    val mach = location.speed / 343.0
                    tvSpeed.text = String.format("%.2f M", mach)
                }
                else -> { // kts
                    val speedKts = location.speed * 1.94384
                    tvSpeed.text = String.format("%.0f kts", speedKts)
                }
            }
        }

        // Altitude
        if (location.hasAltitude()) {
            when (altUnit) {
                "m" -> tvAlt.text = String.format("%.0f m", location.altitude)
                "km" -> tvAlt.text = String.format("%.2f km", location.altitude / 1000.0)
                "bk" -> tvAlt.text = String.format("%.2f bk", location.altitude / 828.0)
                else -> { // ft
                    val altFt = location.altitude * 3.28084
                    tvAlt.text = String.format("%.0f ft", altFt)
                }
            }
            
            // Climb Calculation
            val currentTime = System.currentTimeMillis()
            if (lastTime > 0) {
                val timeDiffMin = (currentTime - lastTime) / 60000.0
                val timeDiffSec = (currentTime - lastTime) / 1000.0
                if (timeDiffMin > 0 && timeDiffSec > 0) {
                    val altDiff = location.altitude - lastAlt
                    verticalSpeedMs = altDiff / timeDiffSec
                    
                    when (climbUnit) {
                        "ms" -> tvClimb.text = String.format("%+d m/s", verticalSpeedMs.toInt())
                        "kmh" -> tvClimb.text = String.format("%+d km/h", (verticalSpeedMs * 3.6).toInt())
                        else -> { // fpm
                            val climbFpm = (altDiff * 3.28084) / timeDiffMin
                            tvClimb.text = String.format("%+d fpm", climbFpm.toInt())
                        }
                    }
                }
            }
            lastAlt = location.altitude
            lastTime = currentTime
        }

        // Climb Angle Calculation
        if (location.hasSpeed() && location.speed > 0.5) { 
            val angle = Math.toDegrees(atan2(verticalSpeedMs, location.speed.toDouble()))
            tvClimbAngle.text = String.format("%+d°", angle.roundToInt())
        } else {
            tvClimbAngle.text = "0°"
        }

        // Heading
        var currentBearing = 0f
        if (location.hasBearing()) {
            currentBearing = location.bearing
            tvHeading.text = String.format("%03d°", currentBearing.toInt())
        } else if (lastLocation != null) {
            val distance = lastLocation!!.distanceTo(location)
            if (distance > 0.5f) { 
                currentBearing = lastLocation!!.bearingTo(location)
                if (currentBearing < 0) currentBearing += 360f
                tvHeading.text = String.format("%03d°", currentBearing.toInt())
            }
        }

        // Update Debug Mode UI if active
        if (prefs.getBoolean("debug_mode", false)) {
            tvDebugLatLon.text = String.format("Lat: %.6f\nLon: %.6f", location.latitude, location.longitude)
            tvDebugAcc.text = if (location.hasAccuracy()) String.format("Acc: %.1fm", location.accuracy) else "Acc: --"
            tvDebugRawSpeed.text = if (location.hasSpeed()) String.format("Speed (m/s): %.2f", location.speed) else "Speed (m/s): --"
            tvDebugRawAlt.text = if (location.hasAltitude()) String.format("Alt (m): %.2f", location.altitude) else "Alt (m): --"
            tvDebugBearing.text = String.format("Bearing: %.1f°", currentBearing)
            ivCompassArrow.rotation = currentBearing
        }

        lastLocation = location

        if (isRecordingRoute && currentRoute != null) {
            val intervalSecs = prefs.getInt("logging_interval", 1)
            val now = System.currentTimeMillis()
            if (now - lastLogTime >= intervalSecs * 1000L) {
                lastLogTime = now
                val pt = RoutePoint(
                    lat = location.latitude,
                    lon = location.longitude,
                    alt = if (location.hasAltitude()) location.altitude else 0.0,
                    speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                    timestamp = now
                )
                
                val prevPt = currentRoute!!.points.lastOrNull()
                currentRoute!!.points.add(pt)
                currentRoute!!.endTime = now
                
                // Draw new segment
                if (prevPt != null) {
                    drawRouteSegment(prevPt, pt)
                }
            }
        }
    }

    private fun toggleRouteRecording(fab: FloatingActionButton) {
        if (!isRecordingRoute) {
            isRecordingRoute = true
            currentRoute = routeManager.startNewRoute()
            lastLogTime = System.currentTimeMillis()
            clearRouteDrawing()
            
            // Add initial point if location available
            lastLocation?.let { loc ->
                currentRoute?.points?.add(RoutePoint(
                    lat = loc.latitude, lon = loc.longitude,
                    alt = if (loc.hasAltitude()) loc.altitude else 0.0,
                    speed = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0,
                    timestamp = lastLogTime
                ))
            }
            
            fab.setImageResource(android.R.drawable.ic_media_pause)
            Toast.makeText(this, "Started Recording Route", Toast.LENGTH_SHORT).show()
        } else {
            val routeToSave = currentRoute
            if (routeToSave == null) {
                isRecordingRoute = false
                fab.setImageResource(android.R.drawable.ic_media_play)
                return
            }

            // Pause recording visually but keep it in state until confirmed
            val input = android.widget.EditText(this)
            input.setText(routeToSave.name)
            
            val dialog = AlertDialog.Builder(this)
                .setTitle("Stop Recording")
                .setMessage("Enter a name for this route:")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val newName = input.text.toString()
                    if (newName.isNotBlank()) {
                        routeToSave.name = newName
                    }
                    routeManager.saveRoute(routeToSave)
                    
                    isRecordingRoute = false
                    currentRoute = null
                    fab.setImageResource(android.R.drawable.ic_media_play)
                    Toast.makeText(this, "Saved Route: ${routeToSave.name}", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Discard") { _, _ ->
                    isRecordingRoute = false
                    currentRoute = null
                    clearRouteDrawing()
                    fab.setImageResource(android.R.drawable.ic_media_play)
                    Toast.makeText(this, "Route Discarded", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    // Resume recording, do nothing
                    Toast.makeText(this, "Recording resumed", Toast.LENGTH_SHORT).show()
                }
                .setCancelable(false)
                .create()
            
            dialog.show()
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
                    0 -> drawFullRoute(route)
                    1 -> showRenameRouteDialog(route)
                    2 -> {
                        routeManager.deleteRoute(route.id)
                        clearRouteDrawing()
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
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearRouteDrawing() {
        for (line in routeLines) {
            map.overlays.remove(line)
        }
        routeLines.clear()
        map.invalidate()
    }

    private fun drawFullRoute(route: RouteData) {
        clearRouteDrawing()
        if (route.points.isEmpty()) return
        
        for (i in 1 until route.points.size) {
            drawRouteSegment(route.points[i - 1], route.points[i])
        }
        
        // Zoom to start
        val startPt = route.points.first()
        map.controller.animateTo(GeoPoint(startPt.lat, startPt.lon))
        map.controller.setZoom(12.0)
        Toast.makeText(this, "Viewing: ${route.name}", Toast.LENGTH_SHORT).show()
    }

    private fun drawRouteSegment(p1: RoutePoint, p2: RoutePoint) {
        val line = org.osmdroid.views.overlay.Polyline(map)
        line.addPoint(GeoPoint(p1.lat, p1.lon))
        line.addPoint(GeoPoint(p2.lat, p2.lon))
        
        // Map altitude to color
        // Assuming 0m to 12000m (approx 40,000 ft) scale
        val maxAlt = 12000.0
        val ratioAlt = (p2.alt / maxAlt).coerceIn(0.0, 1.0)
        
        // Color gradient from blue (low) -> green -> yellow -> red (high)
        val color = when {
            ratioAlt < 0.25 -> android.graphics.Color.rgb(0, (ratioAlt * 4 * 255).toInt(), 255)
            ratioAlt < 0.5 -> android.graphics.Color.rgb(0, 255, (255 - (ratioAlt - 0.25) * 4 * 255).toInt())
            ratioAlt < 0.75 -> android.graphics.Color.rgb(((ratioAlt - 0.5) * 4 * 255).toInt(), 255, 0)
            else -> android.graphics.Color.rgb(255, (255 - (ratioAlt - 0.75) * 4 * 255).toInt(), 0)
        }
        line.outlinePaint.color = color
        
        // Map speed to thickness
        // Assuming 0 to 300 m/s (approx 1000 km/h) scale
        val maxSpeed = 300.0
        val ratioSpeed = (p2.speed / maxSpeed).coerceIn(0.0, 1.0)
        val thickness = 5f + (ratioSpeed * 15f).toFloat() // 5 to 20 thickness
        
        line.outlinePaint.strokeWidth = thickness
        line.outlinePaint.isAntiAlias = true
        
        map.overlays.add(line)
        routeLines.add(line)
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        locationOverlay.enableMyLocation()
        startLocationUpdates()
        checkForSavedMapFile()
        checkIntentForRoute(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { checkIntentForRoute(it) }
    }

    private fun checkIntentForRoute(intent: Intent) {
        val routeId = intent.getStringExtra("view_route_id")
        if (routeId != null) {
            val route = routeManager.getSavedRoutes().find { it.id == routeId }
            if (route != null) {
                drawFullRoute(route)
            }
            // Clear the extra so it doesn't re-trigger on orientation change
            intent.removeExtra("view_route_id")
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationOverlay.disableMyLocation()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
