package com.havish.foreflight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VoyageRecordingService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "voyage_recording"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "VoyageRecordingService"
        private const val AUTO_SAVE_INTERVAL_MS = 30_000L // 30 seconds
    }

    inner class VoyageBinder : Binder() {
        fun getService(): VoyageRecordingService = this@VoyageRecordingService
    }

    private val binder = VoyageBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var voyageManager: VoyageManager? = null

    private var currentVoyage: VoyageData? = null
    private var isRecording = false
    private var isPaused = false
    private var lastLogTime = 0L
    private var autoSaveJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Auto-save on destruction to prevent data loss
        if (isRecording && currentVoyage != null) {
            Log.i(TAG, "Service destroyed while recording - auto-saving voyage")
            saveCurrentVoyage()
        }
        stopLocationUpdates()
        autoSaveJob?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Voyage Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when a voyage is being recorded"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("InTheAir")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("InTheAir")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun startRecording(mode: String, manager: VoyageManager) {
        if (isRecording) return
        
        voyageManager = manager
        currentVoyage = manager.startNewVoyage(mode)
        isRecording = true
        isPaused = false
        lastLogTime = System.currentTimeMillis()

        val notification = buildNotification("Recording voyage...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        startAutoSave()
        Log.i(TAG, "Started recording voyage: ${currentVoyage?.id}")
    }

    fun stopRecording(): VoyageData? {
        val voyage = currentVoyage
        isRecording = false
        isPaused = false
        currentVoyage = null
        
        stopLocationUpdates()
        autoSaveJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.i(TAG, "Stopped recording voyage: ${voyage?.id}")
        return voyage
    }

    fun pauseRecording() {
        if (!isRecording) return
        isPaused = true
        updateNotification("Voyage paused")
        Log.i(TAG, "Paused recording")
    }

    fun resumeRecording() {
        if (!isRecording) return
        isPaused = false
        lastLogTime = System.currentTimeMillis()
        updateNotification("Recording voyage...")
        Log.i(TAG, "Resumed recording")
    }

    fun addNote(text: String, lat: Double, lon: Double) {
        if (!isRecording || currentVoyage == null) return
        val note = VoyageNote(
            timestamp = System.currentTimeMillis(),
            text = text,
            lat = lat,
            lon = lon
        )
        currentVoyage!!.notes.add(note)
        Log.i(TAG, "Added note: $text at ($lat, $lon)")
    }

    fun getVoyageData(): VoyageData? = currentVoyage

    fun isRecording(): Boolean = isRecording

    fun isPaused(): Boolean = isPaused

    fun getRecordedPointCount(): Int = currentVoyage?.points?.size ?: 0

    private fun saveCurrentVoyage() {
        currentVoyage?.let { voyage ->
            voyageManager?.saveVoyage(voyage)
            Log.d(TAG, "Auto-saved voyage with ${voyage.points.size} points")
        }
    }

    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = serviceScope.launch {
            while (isActive && isRecording) {
                delay(AUTO_SAVE_INTERVAL_MS)
                if (isRecording && currentVoyage != null) {
                    saveCurrentVoyage()
                }
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            val prefs = getSharedPreferences("foreflight_prefs", Context.MODE_PRIVATE)
            val intervalSecs = prefs.getInt("logging_interval", 1)

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (!isRecording || isPaused || currentVoyage == null) return
                    
                    for (location in locationResult.locations) {
                        val now = System.currentTimeMillis()
                        if (now - lastLogTime >= intervalSecs * 1000L) {
                            lastLogTime = now
                            val pt = VoyagePoint(
                                lat = location.latitude,
                                lon = location.longitude,
                                alt = if (location.hasAltitude()) location.altitude else 0.0,
                                speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0,
                                timestamp = now
                            )
                            currentVoyage!!.points.add(pt)
                            currentVoyage!!.endTime = now

                            val count = currentVoyage!!.points.size
                            if (count % 10 == 0) {
                                updateNotification("Recording voyage... ($count points)")
                            }
                        }
                    }
                }
            }

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .build()

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, mainLooper)
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }
}
