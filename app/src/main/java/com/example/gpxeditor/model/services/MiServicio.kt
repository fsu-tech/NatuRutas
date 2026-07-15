package com.example.gpxeditor.model.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.gpxeditor.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint

/**
 * Servicio propietario de la captura GPS mientras se graba una ruta.
 *
 * La pantalla de Inicio puede pausarse, destruirse o quedar bloqueada sin interrumpir la
 * recepción de ubicaciones. Cada lote recibido se persiste antes de avisar a la interfaz.
 */
class MiServicio : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val gson = Gson()
    private val preferences by lazy {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private var requestingLocationUpdates = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val locations = locationResult.locations
            if (locations.isEmpty()) return

            val pointsType = object : TypeToken<MutableList<GeoPoint>>() {}.type
            val elevationsType = object : TypeToken<MutableList<Double>>() {}.type
            val pointsJson = preferences.getString(RECORDING_POINTS_KEY, null)
            val elevationsJson = preferences.getString(RECORDING_ELEVATIONS_KEY, null)
            val points: MutableList<GeoPoint> = if (pointsJson.isNullOrEmpty()) {
                mutableListOf()
            } else {
                gson.fromJson(pointsJson, pointsType)
            }
            val elevations: MutableList<Double> = if (elevationsJson.isNullOrEmpty()) {
                mutableListOf()
            } else {
                gson.fromJson(elevationsJson, elevationsType)
            }

            // FusedLocationProvider puede entregar varios puntos juntos al salir del reposo.
            // Se guardan todos para no convertir las curvas recorridas en un único segmento.
            locations.forEach { location ->
                val point = GeoPoint(location.latitude, location.longitude)
                val previous = points.lastOrNull()
                if (previous == null ||
                    previous.latitude != point.latitude ||
                    previous.longitude != point.longitude
                ) {
                    points.add(point)
                    elevations.add(location.altitude)
                }
            }

            preferences.edit()
                .putString(RECORDING_POINTS_KEY, gson.toJson(points))
                .putString(RECORDING_ELEVATIONS_KEY, gson.toJson(elevations))
                .apply()

            val lastLocation = locations.last()
            notifyRecordingUpdated(lastLocation.latitude, lastLocation.longitude)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_ENSURE_RECORDING
        startForeground(NOTIFICATION_ID, buildNotification(paused = false))

        when (action) {
            ACTION_START_RECORDING -> {
                val now = System.currentTimeMillis()
                val startTime = preferences.getLong(START_TIME_KEY, 0L)
                    .takeIf { it > 0L } ?: now
                preferences.edit()
                    .putBoolean(IS_RECORDING_KEY, true)
                    .putBoolean(IS_PAUSED_KEY, false)
                    .putLong(START_TIME_KEY, startTime)
                    .apply()
                startLocationUpdates()
            }

            ACTION_PAUSE_RECORDING -> {
                removeLocationUpdates()
                val pauseStart = preferences.getLong(PAUSE_START_TIME_KEY, 0L)
                    .takeIf { it > 0L } ?: System.currentTimeMillis()
                preferences.edit()
                    .putBoolean(IS_PAUSED_KEY, true)
                    .putLong(PAUSE_START_TIME_KEY, pauseStart)
                    .apply()
                startForeground(NOTIFICATION_ID, buildNotification(paused = true))
            }

            ACTION_RESUME_RECORDING -> {
                val now = System.currentTimeMillis()
                if (preferences.getBoolean(IS_PAUSED_KEY, false)) {
                    val pauseStart = preferences.getLong(PAUSE_START_TIME_KEY, now)
                    val totalPause = preferences.getLong(TOTAL_PAUSE_TIME_KEY, 0L)
                    preferences.edit()
                        .putLong(TOTAL_PAUSE_TIME_KEY, totalPause + (now - pauseStart))
                        .putBoolean(IS_PAUSED_KEY, false)
                        .apply()
                }
                startLocationUpdates()
            }

            ACTION_STOP_RECORDING -> {
                removeLocationUpdates()
                preferences.edit()
                    .putBoolean(IS_RECORDING_KEY, false)
                    .putBoolean(IS_PAUSED_KEY, false)
                    .putLong(END_TIME_KEY, System.currentTimeMillis())
                    .apply()
                notifyRecordingUpdated()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ENSURE_RECORDING -> {
                val isRecording = preferences.getBoolean(IS_RECORDING_KEY, false)
                val isPaused = preferences.getBoolean(IS_PAUSED_KEY, false)
                if (!isRecording) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (isPaused) {
                    removeLocationUpdates()
                    startForeground(NOTIFICATION_ID, buildNotification(paused = true))
                } else {
                    startLocationUpdates()
                }
            }
        }

        notifyRecordingUpdated()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (requestingLocationUpdates) return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        requestingLocationUpdates = true
    }

    private fun removeLocationUpdates() {
        if (!requestingLocationUpdates) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        requestingLocationUpdates = false
    }

    private fun buildNotification(paused: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (paused) "Ruta pausada" else "Grabando ruta")
            .setContentText(
                if (paused) {
                    "NatuRutas conservará el recorrido hasta que lo reanudes"
                } else {
                    "El recorrido continúa aunque apagues la pantalla"
                }
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Grabación de rutas",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene activa la grabación GPS con la pantalla apagada"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notifyRecordingUpdated(latitude: Double? = null, longitude: Double? = null) {
        sendBroadcast(
            Intent(ACTION_RECORDING_UPDATED)
                .setPackage(packageName)
                .apply {
                    latitude?.let { putExtra(EXTRA_LATITUDE, it) }
                    longitude?.let { putExtra(EXTRA_LONGITUDE, it) }
                }
        )
    }

    override fun onDestroy() {
        removeLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_RECORDING =
            "com.example.gpxeditor.action.START_RECORDING"
        const val ACTION_PAUSE_RECORDING =
            "com.example.gpxeditor.action.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING =
            "com.example.gpxeditor.action.RESUME_RECORDING"
        const val ACTION_STOP_RECORDING =
            "com.example.gpxeditor.action.STOP_RECORDING"
        const val ACTION_ENSURE_RECORDING =
            "com.example.gpxeditor.action.ENSURE_RECORDING"
        const val ACTION_RECORDING_UPDATED =
            "com.example.gpxeditor.action.RECORDING_UPDATED"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"

        private const val CHANNEL_ID = "route_recording"
        private const val NOTIFICATION_ID = 1001
        private const val PREFERENCES_NAME = "ruta_data"
        private const val RECORDING_POINTS_KEY = "recording_points"
        private const val RECORDING_ELEVATIONS_KEY = "recording_elevations"
        private const val IS_RECORDING_KEY = "isRecordingHome"
        private const val IS_PAUSED_KEY = "isPausedHome"
        private const val START_TIME_KEY = "startTimeHome"
        private const val END_TIME_KEY = "endTimeHome"
        private const val PAUSE_START_TIME_KEY = "pauseStartTimeHome"
        private const val TOTAL_PAUSE_TIME_KEY = "totalPauseTimeHome"
    }
}
