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
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.io.File
import java.io.FileOutputStream
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
    private var downloadJob: Job? = null

    // Launcher for selecting a .map file
    private val mapFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importAndLoadMapFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Mapsforge graphics factory before anything else
        AndroidGraphicFactory.createInstance(application)

        
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
        editText.threshold = 2

        // Flag to prevent re-querying when user selects an item from dropdown
        var isUserSelecting = false
        // Debounce job to avoid flooding the geocoder on every keystroke
        var searchJob: Job? = null

        // When user taps an item from the dropdown, lock in the selection
        editText.setOnItemClickListener { parent, _, position, _ ->
            isUserSelecting = true
            val selected = parent.getItemAtPosition(position) as String
            editText.setText(selected)
            editText.setSelection(selected.length)
            editText.dismissDropDown()
            // Reset the flag after a short delay so further typing works again
            editText.postDelayed({ isUserSelecting = false }, 300)
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // Don't re-query when an item was just selected from the dropdown
                if (isUserSelecting) return

                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    // Cancel previous search to debounce
                    searchJob?.cancel()
                    searchJob = scope.launch {
                        // Small delay to debounce rapid typing
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
                                // Force the dropdown to show — needed inside AlertDialog
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

        // Also show dropdown when the field gains focus if it already has content
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
        val btnLoadMapFile = view.findViewById<Button>(R.id.btnLoadMapFile)

        setupAutoComplete(etFrom)
        setupAutoComplete(etTo)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        btnDownloadManual.setOnClickListener {
            dialog.dismiss()
            try {
                val boundingBox = map.boundingBox
                Log.d("MapDownload", "Manual download bounding box: $boundingBox")
                downloadMapArea(boundingBox)
            } catch (e: Exception) {
                Log.e("MapDownload", "Error getting bounding box: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnDownloadIndia.setOnClickListener {
            dialog.dismiss()
            downloadIndiaMap()
        }

        btnLoadMapFile.setOnClickListener {
            dialog.dismiss()
            // Open file picker for all file types (to allow .map selection)
            mapFilePicker.launch(arrayOf("*/*"))
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

                        // Small delay to let the map settle before downloading
                        delay(500)
                        downloadMapArea(boundingBox)
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to find one or both locations.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("MapDownload", "Route download error: ${e.message}", e)
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

    /**
     * Downloads map tiles for the given bounding box using our custom
     * TileDownloadHelper, which bypasses osmdroid's CacheManager entirely.
     */
    private fun downloadMapArea(boundingBox: BoundingBox) {
        try {
            Log.d("MapDownload", "Starting download for BB: N=${boundingBox.latNorth}, S=${boundingBox.latSouth}, E=${boundingBox.lonEast}, W=${boundingBox.lonWest}")

            if (boundingBox.latNorth <= boundingBox.latSouth || boundingBox.lonEast <= boundingBox.lonWest) {
                Toast.makeText(this, "Invalid map area. Please try again.", Toast.LENGTH_LONG).show()
                return
            }

            val helper = TileDownloadHelper(map)
            val zoomMin = 5
            val zoomMax = 10

            // Calculate tile count first to show the user
            val tileCount = helper.calculateTiles(boundingBox, zoomMin, zoomMax).size
            val estMinutes = (tileCount * 0.3 / 60).toInt() + 1

            startDownload(helper, boundingBox, zoomMin, zoomMax, "Route Area", tileCount, estMinutes)
        } catch (e: Exception) {
            Log.e("MapDownload", "Error: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Downloads the entire India map (zoom 3-8).
     */
    private fun downloadIndiaMap() {
        val indiaBB = BoundingBox(37.0, 98.0, 6.0, 68.0)
        val zoomMin = 3
        val zoomMax = 8

        val helper = TileDownloadHelper(map)
        val tileCount = helper.calculateTiles(indiaBB, zoomMin, zoomMax).size
        val estMinutes = (tileCount * 0.3 / 60).toInt() + 1

        startDownload(helper, indiaBB, zoomMin, zoomMax, "All India", tileCount, estMinutes)
    }

    /**
     * Shows a confirmation dialog, then starts the download with a progress dialog.
     */
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
                // Cancel any existing download
                downloadJob?.cancel()

                // Create progress dialog
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
                                Toast.makeText(this@MainActivity, "$areaName download complete! ($downloaded tiles)", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@MainActivity, "$areaName: $downloaded tiles downloaded, $errors errors.", Toast.LENGTH_LONG).show()
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

    /**
     * Copies the selected file URI to internal storage and loads it into Mapsforge.
     */
    private fun importAndLoadMapFile(uri: android.net.Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Importing Map")
            .setMessage("Please wait while the map file is copied to the app's secure storage...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        scope.launch {
            try {
                val mapsDir = File(filesDir, "mapsforge")
                mapsDir.mkdirs()
                
                // Clear old maps to save space
                mapsDir.listFiles()?.forEach { it.delete() }

                val destFile = File(mapsDir, "offline_map.map")

                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                progressDialog.dismiss()
                loadMapsforgeFile(destFile)
                
            } catch (e: Exception) {
                progressDialog.dismiss()
                Log.e("Mapsforge", "Error importing map: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Failed to import map: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkForSavedMapFile() {
        val mapsDir = File(filesDir, "mapsforge")
        val mapFile = File(mapsDir, "offline_map.map")
        if (mapFile.exists()) {
            loadMapsforgeFile(mapFile)
        }
    }

    private fun loadMapsforgeFile(file: File) {
        try {
            MapsForgeTileSource.createInstance(application)
            val tileSource = MapsForgeTileSource.createFromFiles(arrayOf(file))
            val tileProvider = MapsForgeTileProvider(
                org.osmdroid.tileprovider.util.SimpleRegisterReceiver(this),
                tileSource,
                null
            )
            
            map.setTileProvider(tileProvider)
            Toast.makeText(this, "Offline vector map loaded successfully!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("Mapsforge", "Error loading map: ${e.message}", e)
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