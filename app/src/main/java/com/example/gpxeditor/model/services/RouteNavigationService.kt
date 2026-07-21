package com.example.gpxeditor.model.services

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.gpxeditor.R
import com.example.gpxeditor.controller.MainActivity
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint
import java.util.Locale
import kotlin.math.*

class RouteNavigationService : Service(), TextToSpeech.OnInitListener {
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var tts: TextToSpeech
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private var route = emptyList<GeoPoint>()
    private var cumulative = doubleArrayOf()
    private var ttsReady = false
    private val pendingSpeech = mutableListOf<Pair<String, String>>()
    private var waitingForRoute = true
    private var offRoute = false
    private var nextEncouragementAt = 0.0
    private var lastProgress = 0.0
    private var requestingUpdates = false
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) { result.lastLocation?.let(::processLocation) }
    }

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        tts = TextToSpeech(this, this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_NAVIGATION) { stopNavigation(); return START_NOT_STICKY }
        if (!loadRoute()) { stopSelf(); return START_NOT_STICKY }
        prefs.edit().putBoolean(NAVIGATION_ACTIVE_KEY, true).apply()
        startForeground(NOTIFICATION_ID, notification("Buscando la ruta cargada…"))
        startLocationUpdates()
        sendStatus(STATUS_APPROACHING, Double.NaN, 0.0)
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = tts.setLanguage(Locale("es", "ES")) >= TextToSpeech.LANG_AVAILABLE
            if (ttsReady) {
                pendingSpeech.forEach { (message, id) -> tts.speak(message, TextToSpeech.QUEUE_ADD, null, id) }
                pendingSpeech.clear()
            }
        }
    }

    private fun loadRoute(): Boolean {
        val json = prefs.getString(ROUTE_POINTS_KEY, null) ?: return false
        val type = object : TypeToken<List<GeoPoint>>() {}.type
        route = runCatching { Gson().fromJson<List<GeoPoint>>(json, type) }.getOrDefault(emptyList())
        if (route.size < 2) return false
        cumulative = DoubleArray(route.size)
        for (i in 1 until route.size) cumulative[i] = cumulative[i - 1] + route[i - 1].distanceToAsDouble(route[i])
        return true
    }

    private fun startLocationUpdates() {
        if (requestingUpdates || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.create().apply { interval = 4000; fastestInterval = 2500; priority = LocationRequest.PRIORITY_HIGH_ACCURACY }
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        requestingUpdates = true
    }

    private fun processLocation(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY) return
        val nearest = nearestRoutePosition(location)
        if (waitingForRoute) {
            if (nearest.distance > ACTIVATION_DISTANCE) {
                updateNotification("A ${nearest.distance.toInt()} m de la ruta")
                sendStatus(STATUS_APPROACHING, nearest.distance, nearest.progress)
                return
            }
            waitingForRoute = false
            lastProgress = nearest.progress
            nextEncouragementAt = floor(lastProgress / ENCOURAGEMENT_INTERVAL) * ENCOURAGEMENT_INTERVAL + ENCOURAGEMENT_INTERVAL
            speak("Ruta encontrada. Comienza la navegación.", "route_started")
        }
        if (!offRoute && nearest.distance >= OFF_ROUTE_DISTANCE) {
            offRoute = true
            speak("Te estás alejando de la ruta. Estás a ${nearest.distance.toInt()} metros.", "off_route")
        } else if (offRoute && nearest.distance <= BACK_ON_ROUTE_DISTANCE) {
            offRoute = false
            speak("Has vuelto a la ruta.", "back_on_route")
            nextEncouragementAt = floor(nearest.progress / ENCOURAGEMENT_INTERVAL) * ENCOURAGEMENT_INTERVAL + ENCOURAGEMENT_INTERVAL
        }
        if (!offRoute && nearest.progress >= nextEncouragementAt) {
            val km = nearest.progress / 1000.0
            val progressText = if (km >= 1.0) " Llevas %.1f kilómetros.".format(Locale("es", "ES"), km) else ""
            speak("Vas bien, sigue así.$progressText", "on_route_${nextEncouragementAt.toInt()}")
            while (nextEncouragementAt <= nearest.progress) nextEncouragementAt += ENCOURAGEMENT_INTERVAL
        }
        lastProgress = max(lastProgress, nearest.progress)
        val status = if (offRoute) STATUS_OFF_ROUTE else STATUS_ON_ROUTE
        val message = if (offRoute) "Fuera de ruta · ${nearest.distance.toInt()} m" else "En ruta · ${formatKm(nearest.progress)} km"
        updateNotification(message)
        sendStatus(status, nearest.distance, nearest.progress)
    }

    private fun nearestRoutePosition(location: Location): RoutePosition {
        var bestDistance = Double.MAX_VALUE; var bestProgress = 0.0
        val latScale = 110_540.0; val lonScale = 111_320.0 * cos(Math.toRadians(location.latitude))
        for (i in 0 until route.lastIndex) {
            val a = route[i]; val b = route[i + 1]
            val ax = (a.longitude - location.longitude) * lonScale; val ay = (a.latitude - location.latitude) * latScale
            val bx = (b.longitude - location.longitude) * lonScale; val by = (b.latitude - location.latitude) * latScale
            val dx = bx - ax; val dy = by - ay; val length2 = dx * dx + dy * dy
            val fraction = if (length2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / length2).coerceIn(0.0, 1.0)
            val distance = hypot(ax + fraction * dx, ay + fraction * dy)
            if (distance < bestDistance) { bestDistance = distance; bestProgress = cumulative[i] + fraction * (cumulative[i + 1] - cumulative[i]) }
        }
        return RoutePosition(bestDistance, bestProgress)
    }

    private fun speak(message: String, id: String) {
        if (ttsReady) tts.speak(message, TextToSpeech.QUEUE_ADD, null, id) else pendingSpeech.add(message to id)
    }
    private fun sendStatus(status: String, distance: Double, progress: Double) {
        sendBroadcast(Intent(ACTION_NAVIGATION_STATUS).setPackage(packageName).apply { putExtra(EXTRA_STATUS, status); putExtra(EXTRA_DISTANCE, distance); putExtra(EXTRA_PROGRESS, progress) })
    }
    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Navegación NatuRutas").setContentText(text)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    private fun updateNotification(text: String) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text)) }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Navegación de rutas", NotificationManager.IMPORTANCE_LOW).apply { description = "Avisos de seguimiento y desvío de una ruta cargada" })
    }
    private fun stopNavigation() {
        if (requestingUpdates) locationClient.removeLocationUpdates(locationCallback)
        requestingUpdates = false; prefs.edit().putBoolean(NAVIGATION_ACTIVE_KEY, false).apply()
        sendStatus(STATUS_STOPPED, Double.NaN, lastProgress); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        if (requestingUpdates) locationClient.removeLocationUpdates(locationCallback)
        prefs.edit().putBoolean(NAVIGATION_ACTIVE_KEY, false).apply(); tts.stop(); tts.shutdown(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private data class RoutePosition(val distance: Double, val progress: Double)
    private fun formatKm(meters: Double) = String.format(Locale("es", "ES"), "%.1f", meters / 1000.0)

    companion object {
        const val ACTION_START_NAVIGATION = "com.example.gpxeditor.action.START_NAVIGATION"
        const val ACTION_STOP_NAVIGATION = "com.example.gpxeditor.action.STOP_NAVIGATION"
        const val ACTION_NAVIGATION_STATUS = "com.example.gpxeditor.action.NAVIGATION_STATUS"
        const val EXTRA_STATUS = "navigation_status"; const val EXTRA_DISTANCE = "distance_to_route"; const val EXTRA_PROGRESS = "route_progress"
        const val STATUS_APPROACHING = "approaching"; const val STATUS_ON_ROUTE = "on_route"; const val STATUS_OFF_ROUTE = "off_route"; const val STATUS_STOPPED = "stopped"
        const val NAVIGATION_ACTIVE_KEY = "route_navigation_active"
        private const val PREFS = "ruta_data"; private const val ROUTE_POINTS_KEY = "ruta_points"; private const val CHANNEL_ID = "route_navigation"; private const val NOTIFICATION_ID = 1002
        private const val ACTIVATION_DISTANCE = 500.0; private const val OFF_ROUTE_DISTANCE = 60.0; private const val BACK_ON_ROUTE_DISTANCE = 35.0; private const val ENCOURAGEMENT_INTERVAL = 500.0; private const val MAX_ACCURACY = 60f
    }
}
