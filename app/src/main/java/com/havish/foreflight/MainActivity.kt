package com.havish.foreflight

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
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

    private lateinit var voyageManager: VoyageManager
    private lateinit var globalNotesManager: GlobalNotesManager
    private val voyageLines = mutableListOf<org.osmdroid.views.overlay.Polyline>()

    private var recordingService: VoyageRecordingService? = null
    private var isBound = false
    private var activeMapNameCache: String? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VoyageRecordingService.VoyageBinder
            recordingService = binder.getService()
            isBound = true
            updateRecordingUI()
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            recordingService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidGraphicFactory.createInstance(application)

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

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()

        setupLocationOverlay()
        setupUI()
        requestPermissions()

        checkForSavedMapFile()
    }

    override fun onStart() {
        super.onStart()
        Intent(this, VoyageRecordingService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AndroidGraphicFactory.clearResourceMemoryCache()
    }

    private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId)!!
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth * 2,
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
        locationProvider.locationUpdateMinTime = 1000
        locationProvider.locationUpdateMinDistance = 1.0f

        locationOverlay = MyLocationNewOverlay(locationProvider, map)
        val planeBitmap = getBitmapFromVectorDrawable(this, R.drawable.ic_plane)
        locationOverlay.setPersonIcon(planeBitmap)
        locationOverlay.setDirectionIcon(planeBitmap)

        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)
    }

    private fun setupUI() {
        voyageManager = VoyageManager(this)
        globalNotesManager = GlobalNotesManager(this)

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) showAddGlobalNoteDialog(p)
                return true
            }
        }
        map.overlays.add(MapEventsOverlay(mapEventsReceiver))

        val fabRecord = findViewById<FloatingActionButton>(R.id.fabRecord)
        fabRecord.setOnClickListener {
            toggleVoyageRecording(fabRecord)
        }
        
        findViewById<FloatingActionButton>(R.id.fabAddNote).setOnClickListener {
            val input = android.widget.EditText(this)
            AlertDialog.Builder(this)
                .setTitle("Add Note")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val text = input.text.toString()
                    if (text.isNotBlank() && lastLocation != null) {
                        recordingService?.addNote(text, lastLocation!!.latitude, lastLocation!!.longitude)
                        drawNoteMarker(lastLocation!!.latitude, lastLocation!!.longitude, text)
                        Toast.makeText(this, "Note added", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<FloatingActionButton>(R.id.fabLocation).setOnClickListener {
            if (locationOverlay.isMyLocationEnabled && locationOverlay.myLocation != null) {
                map.controller.animateTo(locationOverlay.myLocation)
                map.controller.setZoom(14.0)
            } else {
                Toast.makeText(this, "Location not available yet", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<FloatingActionButton>(R.id.fabMenu).setOnClickListener {
            showMainMenu()
        }

        cardDebugMode = findViewById(R.id.cardDebugMode)
        tvDebugLatLon = findViewById(R.id.tvDebugLatLon)
        tvDebugAcc = findViewById(R.id.tvDebugAcc)
        tvDebugRawSpeed = findViewById(R.id.tvDebugRawSpeed)
        tvDebugRawAlt = findViewById(R.id.tvDebugRawAlt)
        tvDebugBearing = findViewById(R.id.tvDebugBearing)
        ivCompassArrow = findViewById(R.id.ivCompassArrow)
    }

    private fun showAddGlobalNoteDialog(p: GeoPoint) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_route_plan, null)
        val etText = view.findViewById<AutoCompleteTextView>(R.id.etFrom)
        val etTag = view.findViewById<AutoCompleteTextView>(R.id.etTo)
        etText.hint = "Note text (e.g. Speed breaker)"
        etTag.hint = "Group/Tag (e.g. Hazard)"
        
        view.findViewById<Button>(R.id.btnDownload).visibility = View.GONE
        view.findViewById<Button>(R.id.btnDownloadManual).visibility = View.GONE
        view.findViewById<Button>(R.id.btnDownloadIndia).visibility = View.GONE

        val tags = globalNotesManager.getTags()
        etTag.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tags))

        AlertDialog.Builder(this)
            .setTitle("Add Global Note")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val text = etText.text.toString().trim()
                val tag = etTag.text.toString().trim().ifBlank { "General" }
                if (text.isNotBlank()) {
                    globalNotesManager.addNote(p.latitude, p.longitude, text, tag)
                    drawGlobalNotes()
                    Toast.makeText(this, "Note saved to $tag", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateRecordingUI() {
        val fab = findViewById<FloatingActionButton>(R.id.fabRecord)
        val fabNote = findViewById<FloatingActionButton>(R.id.fabAddNote)
        if (recordingService?.isRecording() == true) {
            fabNote.visibility = View.VISIBLE
            if (recordingService?.isPaused() == true) {
                fab.setImageResource(android.R.drawable.ic_media_play)
            } else {
                fab.setImageResource(android.R.drawable.ic_media_pause)
            }
        } else {
            fabNote.visibility = View.GONE
            fab.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun showMainMenu() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_main_menu, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.menuItemVoyages).setOnClickListener {
            dialog.dismiss()
            showVoyageBottomSheet()
        }
        view.findViewById<View>(R.id.menuItemOfflineMaps).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, OfflineMapsActivity::class.java))
        }
        view.findViewById<View>(R.id.menuItemNotes)?.setOnClickListener {
            dialog.dismiss()
            showGlobalNotesBottomSheet()
        }
        view.findViewById<View>(R.id.menuItemSettings).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        dialog.show()
    }

    private fun showGlobalNotesBottomSheet() {
        val tags = globalNotesManager.getTags()
        if (tags.isEmpty()) {
            Toast.makeText(this, "No global notes yet. Long press on map to add.", Toast.LENGTH_LONG).show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_bottom_sheet_notes, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(view)

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNoteGroups)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)

        rv.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_note_group, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val tag = tags[position]
                val v = holder.itemView
                v.findViewById<TextView>(R.id.tvTagName).text = tag
                
                val count = globalNotesManager.getNotes().count { it.tag == tag }
                v.findViewById<TextView>(R.id.tvTagCount).text = "$count notes"

                val switchVis = v.findViewById<android.widget.Switch>(R.id.switchVisibility)
                switchVis.isChecked = prefs.getBoolean("tag_vis_$tag", true)
                switchVis.setOnCheckedChangeListener { _, isChecked ->
                    prefs.edit().putBoolean("tag_vis_$tag", isChecked).apply()
                    drawGlobalNotes()
                }
            }
            override fun getItemCount() = tags.size
        }

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun drawGlobalNotes() {
        // Remove existing global markers
        val toRemove = map.overlays.filter { it is org.osmdroid.views.overlay.Marker && it.id == "global_note" }
        map.overlays.removeAll(toRemove)

        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val allNotes = globalNotesManager.getNotes()

        for (note in allNotes) {
            val isVisible = prefs.getBoolean("tag_vis_${note.tag}", true)
            if (isVisible) {
                val marker = org.osmdroid.views.overlay.Marker(map)
                marker.id = "global_note"
                marker.position = GeoPoint(note.lat, note.lon)
                marker.title = "[${note.tag}] ${note.text}"
                marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_note_marker)
                map.overlays.add(marker)
            }
        }
        map.invalidate()
    }

    private fun showVoyageBottomSheet() {
        val voyages = voyageManager.getSavedVoyages()
        if (voyages.isEmpty()) {
            Toast.makeText(this, "No saved voyages", Toast.LENGTH_SHORT).show()
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_bottom_sheet_routes, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(view)

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvVoyages)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        rv.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_route_bottom_sheet, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val voyage = voyages[position]
                val v = holder.itemView
                v.findViewById<TextView>(R.id.tvVoyageName).text = voyage.name
                v.findViewById<TextView>(R.id.tvVoyageInfo).text = "${java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date(voyage.startTime))} -- ${voyage.points.size} points"

                v.findViewById<View>(R.id.ivView).setOnClickListener {
                    dialog.dismiss()
                    drawFullVoyage(voyage)
                }
                v.findViewById<View>(R.id.ivRename).setOnClickListener {
                    dialog.dismiss()
                    showRenameVoyageDialog(voyage)
                }
                v.findViewById<View>(R.id.ivDelete).setOnClickListener {
                    dialog.dismiss()
                    voyageManager.deleteVoyage(voyage.id)
                    clearVoyageDrawing()
                    Toast.makeText(this@MainActivity, "Deleted ${voyage.name}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun getItemCount() = voyages.size
        }

        view.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
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
                                    listOf(name, locality, adminArea, country).filter { part -> part.isNotBlank() }.distinct().joinToString(", ")
                                }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()
                            }

                            if (results.isNotEmpty()) {
                                adapter.clear()
                                adapter.addAll(results)
                                adapter.notifyDataSetChanged()
                                if (editText.isFocused && !isUserSelecting) {
                                    editText.post {
                                        try { editText.showDropDown() } catch (e: Exception) {}
                                    }
                                }
                            }
                        } catch (e: Exception) {}
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
                editText.post { try { editText.showDropDown() } catch (e: Exception) {} }
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

        val dialog = AlertDialog.Builder(this).setView(view).create()

        btnDownloadManual.setOnClickListener {
            dialog.dismiss()
            try {
                val boundingBox = map.boundingBox
                downloadMapArea(boundingBox)
            } catch (e: Exception) {}
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
                    }
                } catch (e: Exception) {}
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
        } catch (e: Exception) {}
        return@withContext null
    }

    private fun downloadMapArea(boundingBox: BoundingBox) {
        try {
            if (boundingBox.latNorth <= boundingBox.latSouth || boundingBox.lonEast <= boundingBox.lonWest) return
            val helper = TileDownloadHelper(map)
            val tileCount = helper.calculateTiles(boundingBox, 5, 10).size
            startDownload(helper, boundingBox, 5, 10, "Area", tileCount, (tileCount * 0.3 / 60).toInt() + 1)
        } catch (e: Exception) {}
    }

    private fun downloadIndiaMap() {
        val helper = TileDownloadHelper(map)
        val bb = BoundingBox(37.0, 98.0, 6.0, 68.0)
        val tileCount = helper.calculateTiles(bb, 3, 8).size
        startDownload(helper, bb, 3, 8, "All India", tileCount, (tileCount * 0.3 / 60).toInt() + 1)
    }

    private fun startDownload(helper: TileDownloadHelper, boundingBox: BoundingBox, zoomMin: Int, zoomMax: Int, areaName: String, tileCount: Int, estMinutes: Int) {
        AlertDialog.Builder(this)
            .setTitle("Download $areaName")
            .setMessage("This will download ~$tileCount tiles.\nEstimated time: ~$estMinutes minutes.")
            .setPositiveButton("Download") { _, _ ->
                val tvProgress = TextView(this)
                val progressDialog = AlertDialog.Builder(this).setTitle("Downloading").setView(tvProgress).setCancelable(false).setNegativeButton("Cancel") { d, _ -> downloadJob?.cancel(); d.dismiss() }.create()
                progressDialog.show()
                downloadJob = helper.downloadArea(boundingBox, zoomMin, zoomMax, object : TileDownloadHelper.Callback {
                    override fun onStart(totalTiles: Int) { tvProgress.text = "Starting..." }
                    override fun onProgress(completed: Int, total: Int, currentZoom: Int) { tvProgress.text = "$completed / $total ($currentZoom)" }
                    override fun onComplete(downloaded: Int, errors: Int) { progressDialog.dismiss() }
                    override fun onError(message: String) { progressDialog.dismiss() }
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkForSavedMapFile() {
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val offlineOnly = prefs.getBoolean("offline_maps_only", false)
        val activeMapName = prefs.getString("active_offline_map", null)

        cardDebugMode.visibility = if (prefs.getBoolean("debug_mode", false)) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvModeIndicator).text = prefs.getString("voyage_mode", "plane")?.uppercase()

        if (!offlineOnly) {
            if (activeMapNameCache != "ONLINE") {
                map.setTileSource(TileSourceFactory.MAPNIK)
                map.setUseDataConnection(true)
                activeMapNameCache = "ONLINE"
            }
            return
        }

        val mapsDir = File(filesDir, "mapsforge")
        val mapToLoad = if (activeMapName != null && File(mapsDir, activeMapName).exists()) {
            activeMapName
        } else {
            mapsDir.listFiles()?.firstOrNull { it.extension == "map" }?.name
        }

        if (mapToLoad != null) {
            if (activeMapNameCache != mapToLoad) {
                loadMapsforgeFile(File(mapsDir, mapToLoad))
                activeMapNameCache = mapToLoad
            }
        } else {
            Toast.makeText(this, "Offline maps only enabled, but no map found.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadMapsforgeFile(file: File) {
        try {
            MapsForgeTileSource.createInstance(application)
            val theme = InternalRenderTheme.OSMARENDER
            val tileSource = MapsForgeTileSource.createFromFiles(arrayOf(file), theme, "OSMARENDER")
            val tileProvider = MapsForgeTileProvider(org.osmdroid.tileprovider.util.SimpleRegisterReceiver(this), tileSource, null)

            map.setTileProvider(tileProvider)
            map.setTileSource(tileSource)
            map.setUseDataConnection(false)

            val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
            val initialZoom = prefs.getFloat("initial_zoom", 14.0f).toDouble()
            map.controller.setZoom(initialZoom)
        } catch (e: Throwable) {
            Toast.makeText(this, "Failed to load offline map", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE)
        val needed = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMinUpdateIntervalMillis(500L).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            fusedLocationClient.lastLocation.addOnSuccessListener { location -> if (location != null) processLocation(location) }
        }
    }

    private fun processLocation(location: Location) {
        val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
        val speedUnit = prefs.getString("unit_speed", "kmh")
        val altUnit = prefs.getString("unit_alt", "m")
        val climbUnit = prefs.getString("unit_climb", "ms")

        var verticalSpeedMs = 0.0

        if (location.hasSpeed()) {
            when (speedUnit) {
                "kmh" -> tvSpeed.text = String.format("%.0f km/h", location.speed * 3.6)
                "mph" -> tvSpeed.text = String.format("%.0f mph", location.speed * 2.23694)
                "mach" -> tvSpeed.text = String.format("%.2f M", location.speed / 343.0)
                else -> tvSpeed.text = String.format("%.0f kts", location.speed * 1.94384)
            }
        }

        if (location.hasAltitude()) {
            when (altUnit) {
                "m" -> tvAlt.text = String.format("%.0f m", location.altitude)
                "km" -> tvAlt.text = String.format("%.2f km", location.altitude / 1000.0)
                "bk" -> tvAlt.text = String.format("%.2f bk", location.altitude / 828.0)
                else -> tvAlt.text = String.format("%.0f ft", location.altitude * 3.28084)
            }
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
                        else -> tvClimb.text = String.format("%+d fpm", ((altDiff * 3.28084) / timeDiffMin).toInt())
                    }
                }
            }
            lastAlt = location.altitude
            lastTime = currentTime
        }

        if (location.hasSpeed() && location.speed > 0.5) {
            val angle = Math.toDegrees(atan2(verticalSpeedMs, location.speed.toDouble()))
            tvClimbAngle.text = String.format("%+d°", angle.roundToInt())
        } else {
            tvClimbAngle.text = "0°"
        }

        var currentBearing = 0f
        if (location.hasBearing()) {
            currentBearing = location.bearing
            tvHeading.text = String.format("%03d°", currentBearing.toInt())
        } else if (lastLocation != null && lastLocation!!.distanceTo(location) > 0.5f) {
            currentBearing = lastLocation!!.bearingTo(location)
            if (currentBearing < 0) currentBearing += 360f
            tvHeading.text = String.format("%03d°", currentBearing.toInt())
        }

        if (prefs.getBoolean("debug_mode", false)) {
            tvDebugLatLon.text = String.format("Lat: %.6f\nLon: %.6f", location.latitude, location.longitude)
            tvDebugAcc.text = if (location.hasAccuracy()) String.format("Acc: %.1fm", location.accuracy) else "Acc: --"
            tvDebugRawSpeed.text = if (location.hasSpeed()) String.format("Speed (m/s): %.2f", location.speed) else "Speed (m/s): --"
            tvDebugRawAlt.text = if (location.hasAltitude()) String.format("Alt (m): %.2f", location.altitude) else "Alt (m): --"
            tvDebugBearing.text = String.format("Bearing: %.1f°", currentBearing)
            ivCompassArrow.rotation = currentBearing
        }

        lastLocation = location

        if (recordingService?.isRecording() == true) {
            val voyage = recordingService?.getVoyageData()
            if (voyage != null && voyage.points.size > voyageLines.size) {
                val startIndex = Math.max(1, voyageLines.size)
                for (i in startIndex until voyage.points.size) {
                    drawVoyageSegment(voyage.points[i - 1], voyage.points[i], voyage.mode)
                }
            }
        }
    }

    private fun toggleVoyageRecording(fab: FloatingActionButton) {
        if (recordingService?.isRecording() == true) {
            if (recordingService?.isPaused() == true) {
                recordingService?.resumeRecording()
                updateRecordingUI()
                Toast.makeText(this, "Recording resumed", Toast.LENGTH_SHORT).show()
            } else {
                val voyage = recordingService?.getVoyageData()
                val input = android.widget.EditText(this)
                input.setText(voyage?.name ?: "")

                AlertDialog.Builder(this)
                    .setTitle("Stop Recording")
                    .setMessage("Enter a name for this voyage:")
                    .setView(input)
                    .setPositiveButton("Save") { _, _ ->
                        val newName = input.text.toString()
                        if (newName.isNotBlank()) voyage?.name = newName
                        voyage?.let { voyageManager.saveVoyage(it) }
                        recordingService?.stopRecording()
                        clearVoyageDrawing()
                        updateRecordingUI()
                        Toast.makeText(this, "Saved Voyage: ${voyage?.name}", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Discard") { _, _ ->
                        AlertDialog.Builder(this)
                            .setTitle("Confirm Discard")
                            .setMessage("Are you sure you want to discard this voyage?")
                            .setPositiveButton("Discard") { _, _ ->
                                val id = voyage?.id
                                recordingService?.stopRecording()
                                if (id != null) voyageManager.deleteVoyage(id)
                                clearVoyageDrawing()
                                updateRecordingUI()
                                Toast.makeText(this, "Voyage Discarded", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .setNegativeButton("Pause") { _, _ ->
                        recordingService?.pauseRecording()
                        updateRecordingUI()
                        Toast.makeText(this, "Recording paused", Toast.LENGTH_SHORT).show()
                    }
                    .setCancelable(false)
                    .show()
            }
        } else {
            val mode = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE).getString("voyage_mode", "plane") ?: "plane"
            val intent = Intent(this, VoyageRecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            recordingService?.startRecording(mode, voyageManager)
            clearVoyageDrawing()
            updateRecordingUI()
            Toast.makeText(this, "Started Recording Voyage", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameVoyageDialog(voyage: VoyageData) {
        val input = android.widget.EditText(this)
        input.setText(voyage.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Voyage")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank()) {
                    voyageManager.renameVoyage(voyage.id, newName)
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearVoyageDrawing() {
        for (line in voyageLines) map.overlays.remove(line)
        voyageLines.clear()
        
        val toRemove = map.overlays.filter { it is org.osmdroid.views.overlay.Marker }
        map.overlays.removeAll(toRemove)
        
        map.invalidate()
    }

    private fun drawFullVoyage(voyage: VoyageData) {
        clearVoyageDrawing()
        if (voyage.points.isEmpty()) return

        for (i in 1 until voyage.points.size) {
            drawVoyageSegment(voyage.points[i - 1], voyage.points[i], voyage.mode)
        }
        
        for (note in voyage.notes) {
            drawNoteMarker(note.lat, note.lon, note.text)
        }

        val startPt = voyage.points.first()
        map.controller.animateTo(GeoPoint(startPt.lat, startPt.lon))
        map.controller.setZoom(12.0)
        Toast.makeText(this, "Viewing: ${voyage.name}", Toast.LENGTH_SHORT).show()
    }
    
    private fun drawNoteMarker(lat: Double, lon: Double, text: String) {
        val marker = org.osmdroid.views.overlay.Marker(map)
        marker.position = GeoPoint(lat, lon)
        marker.title = text
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_note_marker)
        map.overlays.add(marker)
        map.invalidate()
    }

    private fun drawVoyageSegment(p1: VoyagePoint, p2: VoyagePoint, mode: String) {
        val line = org.osmdroid.views.overlay.Polyline(map)
        line.addPoint(GeoPoint(p1.lat, p1.lon))
        line.addPoint(GeoPoint(p2.lat, p2.lon))

        var maxAlt = 12000.0
        var maxSpeed = 300.0

        if (mode == "car") {
            maxAlt = 609.0
            maxSpeed = 55.5
        }

        var ratioAlt = p2.alt / maxAlt
        if (mode == "car" && ratioAlt > 1.0) ratioAlt = 1.0 + Math.log(ratioAlt) * 0.1
        ratioAlt = ratioAlt.coerceIn(0.0, 1.0)

        val color = when {
            ratioAlt < 0.25 -> android.graphics.Color.rgb(0, (ratioAlt * 4 * 255).toInt(), 255)
            ratioAlt < 0.5 -> android.graphics.Color.rgb(0, 255, (255 - (ratioAlt - 0.25) * 4 * 255).toInt())
            ratioAlt < 0.75 -> android.graphics.Color.rgb(((ratioAlt - 0.5) * 4 * 255).toInt(), 255, 0)
            else -> android.graphics.Color.rgb(255, (255 - (ratioAlt - 0.75) * 4 * 255).toInt(), 0)
        }
        line.outlinePaint.color = color

        var ratioSpeed = p2.speed / maxSpeed
        if (mode == "car" && ratioSpeed > 1.0) ratioSpeed = 1.0 + Math.log(ratioSpeed) * 0.2
        ratioSpeed = ratioSpeed.coerceIn(0.0, 1.0)
        val thickness = 5f + (ratioSpeed * 15f).toFloat()

        line.outlinePaint.strokeWidth = thickness
        line.outlinePaint.isAntiAlias = true

        map.overlays.add(line)
        voyageLines.add(line)
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        locationOverlay.enableMyLocation()
        startLocationUpdates()
        checkForSavedMapFile()
        checkIntentForRoute(intent)
        updateRecordingUI()
        drawGlobalNotes()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { checkIntentForRoute(it) }
    }

    private fun checkIntentForRoute(intent: Intent) {
        val voyageId = intent.getStringExtra("view_voyage_id")
        if (voyageId != null) {
            val voyage = voyageManager.getSavedVoyages().find { it.id == voyageId }
            if (voyage != null) drawFullVoyage(voyage)
            intent.removeExtra("view_voyage_id")
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        locationOverlay.disableMyLocation()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
