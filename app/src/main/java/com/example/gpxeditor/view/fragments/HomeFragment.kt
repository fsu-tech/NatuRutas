
package com.example.gpxeditor.view.fragments

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.ImageView
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.gpxeditor.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import com.example.gpxeditor.model.database.DatabaseHelper
import com.example.gpxeditor.model.services.MiServicio
import org.osmdroid.views.overlay.Overlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.xmlpull.v1.XmlPullParserFactory
import java.text.ParseException



class HomeFragment : Fragment(R.layout.fragment_home) {

        private lateinit var btnStartRoute: Button
        private lateinit var btnStopRoute: Button
        private lateinit var btnSaveRoute: Button
        private lateinit var btnPauseRoute: Button
        private lateinit var btnResumeRoute: Button
        private var isRecording: Boolean = false
        private var isPaused: Boolean = false
        private var startTime: Long = 0
        private var endTime: Long = 0
        private var pauseStartTime: Long = 0
        private var totalPauseTime: Long = 0
    private lateinit var mapView: MapView
    private val READ_STORAGE_PERMISSION_CODE = 101
    private val LOCATION_PERMISSION_CODE = 102
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationMarker: Marker? = null
    private var polyline: Polyline? = null // Polyline de la ruta cargada
    private var recordingPolyline: Polyline? = null // Polyline de la ruta grabada
    private val gpxOverlays = mutableListOf<Overlay>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadJob: Job? = null
    private var isNavigating = false
    private var lastLocation: Location? = null
    private var isAppClosing = false
    private var isLocationUpdatesActive = false
    private var currentTimes: Pair<Long?, Long?>? = null
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private val RUTA_POINTS_KEY = "ruta_points"
    private val WAYPOINTS_KEY = "waypoints"
    private val ELEVATIONS_KEY = "elevations"
    private val CURRENT_ROUTE_NAME_KEY = "current_route_name"
    private val RECORDING_POINTS_KEY = "recording_points"
    private val RECORDING_ELEVATIONS_KEY = "recording_elevations"
    private val RECORDING_WAYPOINTS_KEY = "recording_waypoints"

    private var currentPoints: MutableList<GeoPoint>? = null
    private var currentWaypoints: List<WaypointInfo>? = null
    private var currentElevations: MutableList<Double>? = null // Ahora mutable para grabación
    private var currentRouteName: String? = null
    private var currentDistance: Double = 0.0
    private lateinit var dbHelper: DatabaseHelper
    private var tiempoEntrada: Long = 0

    // Flag para evitar animación en el primer callback tras volver a la pestaña
    private var skipNextLocationAnimation = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            if (isAdded && isResumed) {
                locationResult.lastLocation?.let { location ->
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    updateUserLocation(geoPoint, skipAnimation = skipNextLocationAnimation)
                    lastLocation = location
                    // Si el flag está activo, lo desactivamos tras el primer callback
                    if (skipNextLocationAnimation) {
                        skipNextLocationAnimation = false
                    }
                }
            }
        }
    }

    private var recordingReceiverRegistered = false
    private val recordingUpdatesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isAdded || view == null) return
            syncRecordingFromService(
                latitude = intent?.takeIf {
                    it.hasExtra(MiServicio.EXTRA_LATITUDE)
                }?.getDoubleExtra(MiServicio.EXTRA_LATITUDE, 0.0),
                longitude = intent?.takeIf {
                    it.hasExtra(MiServicio.EXTRA_LONGITUDE)
                }?.getDoubleExtra(MiServicio.EXTRA_LONGITUDE, 0.0)
            )
        }
    }

    /** Guarda los puntos, elevaciones y puntos de interés de la grabación. */
    private fun saveRecordingPoints() {
        val editor = sharedPreferences.edit()
        // Guardar también las listas vacías evita recuperar por error una grabación anterior
        // si el usuario inicia una nueva ruta y sale antes de recibir la primera ubicación.
        editor.putString(RECORDING_POINTS_KEY, gson.toJson(currentPoints.orEmpty()))
        editor.putString(RECORDING_ELEVATIONS_KEY, gson.toJson(currentElevations.orEmpty()))
        val recordingWaypoints = if (currentPoints.isNullOrEmpty()) emptyList() else currentWaypoints.orEmpty()
        editor.putString(RECORDING_WAYPOINTS_KEY, gson.toJson(recordingWaypoints))
        editor.apply()
    }

    /** Actualiza únicamente los puntos de interés sin sobrescribir puntos GPS del servicio. */
    private fun saveRecordingWaypoints() {
        sharedPreferences.edit()
            .putString(
                RECORDING_WAYPOINTS_KEY,
                gson.toJson(
                    if (currentPoints.isNullOrEmpty()) {
                        emptyList<WaypointInfo>()
                    } else {
                        currentWaypoints.orEmpty()
                    }
                )
            )
            .apply()
    }

    private fun syncRecordingFromService(latitude: Double? = null, longitude: Double? = null) {
        restoreRecordingPoints()
        recalculateDistanceFromPoints()
        loadRouteData()

        val hasRecordingPoints = !currentPoints.isNullOrEmpty()
        if (hasRecordingPoints) {
            if (recordingPolyline == null) {
                recordingPolyline = Polyline().apply {
                    outlinePaint.color = Color.parseColor("#FF9800")
                    outlinePaint.strokeWidth = 8f
                }
                mapView.overlays.add(recordingPolyline)
            }
            recordingPolyline?.setPoints(currentPoints)
        }

        if (latitude != null && longitude != null) {
            updateUserLocation(GeoPoint(latitude, longitude))
        } else if (isRecording && !currentPoints.isNullOrEmpty()) {
            updateUserLocation(currentPoints!!.last())
        }
        mapView.invalidate()
    }

    private fun sendRecordingCommand(action: String) {
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), MiServicio::class.java).setAction(action)
        )
    }

    /** Restaura todos los datos temporales de la grabación desde SharedPreferences. */
    private fun restoreRecordingPoints() {
        val recordingPointsJson = sharedPreferences.getString(RECORDING_POINTS_KEY, null)
        val recordingElevationsJson = sharedPreferences.getString(RECORDING_ELEVATIONS_KEY, null)
        val recordingWaypointsJson = sharedPreferences.getString(RECORDING_WAYPOINTS_KEY, null)
        if (!recordingPointsJson.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<GeoPoint>>() {}.type
            currentPoints = gson.fromJson(recordingPointsJson, type)
        } else {
            currentPoints = mutableListOf()
        }
        if (!recordingElevationsJson.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<Double>>() {}.type
            currentElevations = gson.fromJson(recordingElevationsJson, type)
        } else {
            currentElevations = mutableListOf()
        }
        if (!recordingWaypointsJson.isNullOrEmpty()) {
            val type = object : TypeToken<MutableList<WaypointInfo>>() {}.type
            currentWaypoints = gson.fromJson(recordingWaypointsJson, type)
        } else {
            // Una ruta sin puntos de interés sigue siendo una ruta válida.
            currentWaypoints = mutableListOf()
        }
    }

    /** Recalcula la distancia total usando todos los puntos grabados */
    private fun recalculateDistanceFromPoints() {
        currentDistance = 0.0
        val points = currentPoints
        if (points != null && points.size > 1) {
            for (i in 1 until points.size) {
                currentDistance += points[i - 1].distanceToAsDouble(points[i])
            }
        }
    }

    data class WaypointInfo(
        val geoPoint: GeoPoint,
        var name: String,
        var description: String,
        var photoUrl: String,
        var userPhotoUrl: String = "",
        var time: Long? = null
    )

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sharedPreferences = context.getSharedPreferences("ruta_data", Context.MODE_PRIVATE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().load(requireContext(), requireActivity().getPreferences(0))
        mapView = view.findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Restaurar puntos y distancia de la grabación al volver al fragmento (después de inicializar mapView)
        restoreRecordingPoints()
        recalculateDistanceFromPoints()
        // Si hay puntos, dibujar la polyline
        if (!currentPoints.isNullOrEmpty()) {
            if (recordingPolyline == null) {
                recordingPolyline = Polyline().apply {
                    outlinePaint.color = Color.parseColor("#FF9800")
                    outlinePaint.strokeWidth = 8f
                }
                mapView.overlays.add(recordingPolyline)
            }
            recordingPolyline?.setPoints(currentPoints)
            mapView.invalidate()
        }

        dbHelper = DatabaseHelper(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Usar la vista general de España si no hay grabación ni puntos grabados.
        val recordingPointsJson = requireContext().getSharedPreferences("ruta_data", Context.MODE_PRIVATE).getString(RECORDING_POINTS_KEY, null)
        val isRecordingSaved = requireContext().getSharedPreferences("ruta_data", Context.MODE_PRIVATE).getBoolean("isRecordingHome", false)
        var shouldUseSpainOverview = true
        if (isRecordingSaved && recordingPointsJson != null) {
            // Hay grabación activa y puntos grabados: conservar su encuadre.
            shouldUseSpainOverview = false
        }
        if (shouldUseSpainOverview) {
            showSpainOverview()
        }

        btnStartRoute = view.findViewById(R.id.btnStartRoute)
        btnStopRoute = view.findViewById(R.id.btnStopRoute)
        btnSaveRoute = view.findViewById(R.id.btnSaveRoute)
        btnPauseRoute = view.findViewById(R.id.btnPauseRoute)
        btnResumeRoute = view.findViewById(R.id.btnResumeRoute)

        btnStartRoute.setOnClickListener {
            dbHelper.registrarEvento("Grabacion", "iniciar_grabacion", null)
            startRecording()
        }
        btnStopRoute.setOnClickListener {
            dbHelper.registrarEvento("Grabacion", "parar_grabacion", null)
            stopRecording()
        }
        btnSaveRoute.setOnClickListener {
            dbHelper.registrarEvento("Grabacion", "guardar_ruta", null)
            mostrarDialogoGuardarRuta()
        }
        btnPauseRoute.setOnClickListener {
            dbHelper.registrarEvento("Grabacion", "pausar_grabacion", null)
            pauseRoute()
        }
        btnResumeRoute.setOnClickListener {
            dbHelper.registrarEvento("Grabacion", "reanudar_grabacion", null)
            resumeRoute()
        }

        view.findViewById<Button?>(R.id.btnLoadGpx)?.setOnClickListener {
            selectGpxFile()
        }

        view.findViewById<Button?>(R.id.btnRemoveLoadedRoute)?.setOnClickListener {
            polyline?.let {
                mapView.overlays.remove(it)
                polyline = null
            }
            gpxOverlays.forEach { overlay ->
                mapView.overlays.remove(overlay)
            }
            gpxOverlays.clear()
            limpiarRutaEnSharedPreferences()
            Toast.makeText(requireContext(), "Ruta cargada eliminada", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button?>(R.id.btn_add_poi)?.setOnClickListener {
            addPointOfInterest()
        }

        updateUI(false)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    limpiarRecursos()
                    requireActivity().finish()
                }
            }
        )

        restoreRouteFromPrefs()
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                loadGpxFromUri(uri)
            }
        }
    }

    private fun limpiarRecursos() {
        Log.d("HomeFragment", "limpiarRecursos called")
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesActive = false
        lastLocation = null
        mapView.overlays.removeAll(gpxOverlays.toList())
        gpxOverlays.clear()
        recordingPolyline?.let { mapView.overlays.remove(it) }
        recordingPolyline = null
        currentPoints = null
        mapView.invalidate()
        loadJob?.cancel()
        loadJob = null
    }

    private fun limpiarRutaEnSharedPreferences() {
        Log.d("HomeFragment", "limpiarRutaEnSharedPreferences called")
        val editor = sharedPreferences.edit()
        editor.remove(RUTA_POINTS_KEY)
        editor.remove(WAYPOINTS_KEY)
        editor.remove(ELEVATIONS_KEY)
        editor.remove(CURRENT_ROUTE_NAME_KEY)
        editor.apply()
    }

    fun openGpxFile(uri: Uri) {
        loadGpxFromUri(uri)
    }

    private fun loadGpxFromUri(uri: Uri) {
        limpiarRecursos()
        loadJob = scope.launch(Dispatchers.IO) {
            try {
                requireActivity().contentResolver.openInputStream(uri)?.use { inputStream ->
                    val (routeData, waypoints, routeName) = parseGpx(inputStream)
                    val (points, elevations, times) = routeData
                    withContext(Dispatchers.Main) {
                        if (isActive && isAdded && view != null) {
                            currentPoints = null // Solo para grabación
                            currentWaypoints = waypoints
                            currentElevations = elevations.toMutableList()
                            currentRouteName = routeName
                            currentTimes = times

                            dbHelper.registrarEvento("Inicio", "gpx_cargado", routeName ?: "sin_nombre")
                            drawGpxRoute(points, waypoints)

                            guardarRutaEnSharedPreferences(points, waypoints, elevations, routeName)
                        }
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        Toast.makeText(requireContext(), "Error al leer el archivo GPX", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: XmlPullParserException) {
                withContext(Dispatchers.Main) {
                    if (isAdded && view != null) {
                        Toast.makeText(requireContext(), "Error al analizar el archivo GPX", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun guardarRutaEnSharedPreferences(points: List<GeoPoint>, waypoints: List<WaypointInfo>, elevations: List<Double>, routeName: String?) {
        val editor = sharedPreferences.edit()

        val pointsJson = gson.toJson(points)
        val waypointsJson = gson.toJson(waypoints)
        val elevationsJson = gson.toJson(elevations)

        editor.putString(RUTA_POINTS_KEY, pointsJson)
        editor.putString(WAYPOINTS_KEY, waypointsJson)
        editor.putString(ELEVATIONS_KEY, elevationsJson)
        editor.putString(CURRENT_ROUTE_NAME_KEY, routeName)

        editor.apply()
    }

    private fun selectGpxFile() {
        launchFilePicker()
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/gpx+xml", "text/xml", "application/xml", "application/octet-stream")
            )
        }
        filePickerLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            READ_STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    launchFilePicker()
                } else {
                    Toast.makeText(requireContext(), "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
                }
            }
            LOCATION_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                } else {
                    Toast.makeText(requireContext(), "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido
                    // Continúa con la lógica de notificación
                } else {
                    // Permiso denegado
                    // Maneja el caso en que el usuario deniega el permiso
                    Toast.makeText(requireContext(), "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleIntent() {
        val intent = requireActivity().intent
        val action = intent.action
        val data = intent.data

        if (Intent.ACTION_VIEW == action && data != null) {
            openGpxFile(data)
        }
    }

    private fun parseGpx(inputStream: InputStream): Triple<Triple<List<GeoPoint>, List<Double>, Pair<Long?, Long?>>, List<WaypointInfo>, String?> {
        val points = mutableListOf<GeoPoint>()
        val elevations = mutableListOf<Double>()
        val waypoints = mutableListOf<WaypointInfo>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var lat = 0.0
        var lon = 0.0
        var name = ""
        var desc = "" // Inicialmente vacía, se llenará con name si no hay desc
        var photoUrl = ""
        var ele = 0.0
        var routeName: String? = null
        var startTime: Long? = null
        var endTime: Long? = null
        var isInsideTrkpt = false

        // Intentar parsear la fecha con diferentes formatos comunes
        val dateFormats = arrayOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US) // Para formatos con offset
        )

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "trkpt" -> {
                        lat = parser.getAttributeValue(null, "lat").toDouble()
                        lon = parser.getAttributeValue(null, "lon").toDouble()
                        isInsideTrkpt = true
                        Log.d("GPXParser", "Punto encontrado: lat=$lat, lon=$lon")
                    }
                    "wpt" -> {
                        lat = parser.getAttributeValue(null, "lat").toDouble()
                        lon = parser.getAttributeValue(null, "lon").toDouble()
                        name = ""
                        desc = "" // Reiniciar para cada waypoint
                        photoUrl = ""
                        Log.d("GPXParser", "Waypoint encontrado: lat=$lat, lon=$lon")
                    }
                    "name" -> {
                        if (parser.next() == XmlPullParser.TEXT) {
                            val text = parser.text.trim()
                            if (routeName == null) {
                                routeName = text
                                Log.d("GPXParser", "Nombre de ruta encontrado: $routeName")
                            } else {
                                name = text
                                desc = text // Usar el nombre como descripción por defecto
                                Log.d("GPXParser", "Nombre de waypoint encontrado: $name")
                            }
                        }
                    }
                    "desc", "cmt" -> { // Soporte para desc y cmt
                        while (parser.next() != XmlPullParser.END_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
                            if (parser.eventType == XmlPullParser.TEXT) {
                                val text = parser.text.trim()
                                if (text.isNotEmpty()) { // Solo actualizar si hay texto
                                    desc = text
                                    Log.d("GPXParser", "Descripción encontrada en <${parser.name}>: $desc")
                                }
                                break
                            }
                        }
                        if (desc.isEmpty()) {
                            Log.d("GPXParser", "No se encontró texto válido en <${parser.name}>")
                        }
                    }
                    "link" -> {
                        photoUrl = parser.getAttributeValue(null, "href")
                        Log.d("GPXParser", "Foto encontrada: $photoUrl")
                    }
                    "ele" -> {
                        if (parser.next() == XmlPullParser.TEXT) {
                            ele = parser.text.toDouble()
                            elevations.add(ele)
                            Log.d("GPXParser", "Elevación encontrada: $ele")
                        }
                    }
                    "time" -> {
                        if (parser.next() == XmlPullParser.TEXT && isInsideTrkpt) {
                            val timeString = parser.text
                            var parsedDate: Date? = null
                            for (format in dateFormats) {
                                try {
                                    parsedDate = format.parse(timeString)
                                    break // Si se parsea con éxito, salir del bucle
                                } catch (e: ParseException) {
                                    // Intentar con el siguiente formato
                                }
                            }

                            parsedDate?.let {
                                val timeMillis = it.time
                                if (startTime == null) {
                                    startTime = timeMillis
                                    Log.d("GPXParser", "Tiempo de inicio encontrado: $startTime")
                                }
                                endTime = timeMillis
                                Log.d("GPXParser", "Tiempo encontrado: $endTime")
                            } ?: run {
                                Log.e("GPXParser", "Error al parsear el tiempo: Formato desconocido: $timeString")
                            }
                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "trkpt" -> {
                        points.add(GeoPoint(lat, lon))
                        isInsideTrkpt = false
                    }
                    "wpt" -> {
                        if (desc.isEmpty()) desc = name // Asegurar que desc tenga al menos el nombre
                        waypoints.add(WaypointInfo(GeoPoint(lat, lon), name, desc, photoUrl))
                        Log.d("GPXParser", "Waypoint añadido: name=$name, desc=$desc, photoUrl=$photoUrl")
                    }
                }
            }
            eventType = parser.next()
        }

        Log.d("GPXParser", "Tiempos extraídos: startTime=$startTime, endTime=$endTime")
        Log.d("GPXParser", "Total waypoints procesados: ${waypoints.size}")

        return Triple(Triple(points, elevations, Pair(startTime, endTime)), waypoints, routeName)
    }



    private fun drawGpxRoute(points: List<GeoPoint>, waypoints: List<WaypointInfo>) {
        // Elimina solo la polyline de la ruta cargada y sus marcadores, pero no la de grabación
        polyline?.let { mapView.overlays.remove(it) }
        gpxOverlays.forEach { mapView.overlays.remove(it) }
        gpxOverlays.clear()

        if (points.isEmpty()) {
            Toast.makeText(requireContext(), "No se encontraron puntos de ruta en el archivo GPX", Toast.LENGTH_SHORT).show()
            return
        }

        polyline = Polyline().apply {
            setPoints(points)
            outlinePaint.color = Color.parseColor("#1976D2") // Azul fuerte
            outlinePaint.strokeWidth = 8f
        }
        polyline?.let {
            mapView.overlays.add(it)
        }

        mapView.controller.setCenter(points.first())
        mapView.controller.setZoom(15.0)


        // Usar drawables independientes para cada marcador
        val startDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map)?.mutate()
        startDrawable?.setTint(Color.GREEN)
        val startMarker = Marker(mapView)
        startMarker.position = points.first()
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.title = "Inicio de la ruta"
        startMarker.icon = startDrawable
        gpxOverlays.add(startMarker)
        mapView.overlays.add(startMarker)

        val endDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_map)?.mutate()
        endDrawable?.setTint(Color.RED)
        val endMarker = Marker(mapView)
        endMarker.position = points.last()
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        endMarker.title = "Fin de la ruta"
        endMarker.icon = endDrawable
        gpxOverlays.add(endMarker)
        mapView.overlays.add(endMarker)

        waypoints.forEach { waypointInfo ->
            val waypointMarker = Marker(mapView)
            waypointMarker.position = waypointInfo.geoPoint
            waypointMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            waypointMarker.title = waypointInfo.name
            waypointMarker.snippet = waypointInfo.description
            waypointMarker.icon.setTint(Color.BLUE)

            waypointMarker.setOnMarkerClickListener { marker, _ ->
                dbHelper.registrarEvento("Inicio", "click_waypoint", waypointInfo.name)
                showWaypointDialog(waypointInfo)
                true
            }

            gpxOverlays.add(waypointMarker)
            mapView.overlays.add(waypointMarker)
        }

        mapView.invalidate()
    }

    private fun showWaypointDialog(waypointInfo: WaypointInfo) {
        val builder = AlertDialog.Builder(requireContext())


        val photoUrl = if (waypointInfo.userPhotoUrl.isNotEmpty()) {
            waypointInfo.userPhotoUrl
        } else {
            waypointInfo.photoUrl
        }

        val message = if (photoUrl.isNotEmpty()) {
            Html.fromHtml("${waypointInfo.description}<br><a href=\"$photoUrl\">Ver foto</a>")
        } else {
            waypointInfo.description
        }

        builder.setMessage(message) // Mostrar el mensaje con o sin el link.
        builder.setPositiveButton("Cerrar", null)
        builder.setNeutralButton("Añadir/Editar foto y comentario") { _, _ ->
            showAddPhotoDialog(waypointInfo)
        }

        val dialog = builder.create()
        dialog.show()

        dialog.findViewById<TextView>(android.R.id.message)?.let { messageTextView ->
            messageTextView.movementMethod = LinkMovementMethod.getInstance()

            if (photoUrl.isNotEmpty()) {
                messageTextView.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(photoUrl))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "No se puede abrir la foto", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun showAddPhotoDialog(waypointInfo: WaypointInfo) {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_photo, null)
        val etPhotoUrl = view.findViewById<EditText>(R.id.et_photo_url)
        val etDescription = view.findViewById<EditText>(R.id.et_description) // Obtener el EditText de la descripción

        etPhotoUrl.setText(waypointInfo.userPhotoUrl)
        etDescription.setText(waypointInfo.description) // Establecer la descripción existente

        AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle("Añadir/Editar foto y descripción") // Actualizar el título
            .setPositiveButton("Guardar") { _, _ ->
                waypointInfo.userPhotoUrl = etPhotoUrl.text.toString()
                waypointInfo.description = etDescription.text.toString() // Actualizar la descripción
                guardarRutaEnSharedPreferences(currentPoints!!, currentWaypoints!!, currentElevations!!, currentRouteName)
                drawGpxRoute(currentPoints!!, currentWaypoints!!)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_CODE)
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            isLocationUpdatesActive = true
        }
    }

    private fun updateUserLocation(geoPoint: GeoPoint, skipAnimation: Boolean = false) {
        if (isAdded && isResumed && mapView != null) {
            if (locationMarker == null) {
                locationMarker = Marker(mapView)
                val icon: Drawable? = ContextCompat.getDrawable(requireContext(),
                    R.drawable.ic_blue_dot
                )
                locationMarker?.icon = icon
                mapView.overlays.add(locationMarker)
            }

            locationMarker?.position = geoPoint
            mapView.invalidate()

            // Mantener el punto azul visible durante toda la grabación activa.
            if (isRecording && !isPaused) {
                mapView.controller.setCenter(geoPoint)
                mapView.controller.setZoom(17.0)
            }
        }
    }

    private fun calculateDistanceToRoute(userLocation: GeoPoint, routePoints: List<GeoPoint>): Float {
        if (routePoints.isEmpty()) return Float.MAX_VALUE

        val userLocationLocation = Location("user").apply {
            latitude = userLocation.latitude
            longitude = userLocation.longitude
        }

        var minDistance = Float.MAX_VALUE

        for (i in 0 until routePoints.size - 1) {
            val startPoint = routePoints[i]
            val endPoint = routePoints[i + 1]

            val startLocation = Location("start").apply {
                latitude = startPoint.latitude
                longitude = startPoint.longitude
            }
            val endLocation = Location("end").apply {
                latitude = endPoint.latitude
                longitude = endPoint.longitude
            }

            val distance = calculateDistanceToSegment(userLocationLocation, startLocation, endLocation)
            minDistance = minOf(minDistance, distance)
        }

        return minDistance
    }

    private fun calculateDistanceToSegment(userLocation: Location, startLocation: Location, endLocation: Location): Float {
        val px = endLocation.longitude - startLocation.longitude
        val py = endLocation.latitude - startLocation.latitude
        val temp = (px * px) + (py * py)

        if (temp > 0) {
            val u = ((userLocation.longitude - startLocation.longitude) * px + (userLocation.latitude - startLocation.latitude) * py) / temp

            if (u > 1) {
                return userLocation.distanceTo(endLocation)
            } else if (u < 0) {
                return userLocation.distanceTo(startLocation)
            } else {
                val x = startLocation.longitude + u * px
                val y = startLocation.latitude + u * py

                val segmentLocation = Location("segment").apply {
                    latitude = y
                    longitude = x
                }

                return userLocation.distanceTo(segmentLocation)
            }
        } else {
            return userLocation.distanceTo(startLocation)
        }
    }

    private fun guardarRutaEnBaseDeDatos() {
        Log.d("guardarRuta", "Inicio de guardarRutaEnBaseDeDatos")

        // Solo permitir guardar si hay ruta grabada (naranja)
        if (currentPoints == null || currentPoints!!.size <= 1) {
            Toast.makeText(requireContext(), "Primero graba una ruta para poder guardarla", Toast.LENGTH_SHORT).show()
            Log.e("guardarRuta", "Intento de guardar sin ruta grabada")
            return
        }

        val routeElevations = currentElevations
        if (routeElevations == null) {
            Toast.makeText(requireContext(), "Faltan elevaciones para guardar la ruta", Toast.LENGTH_SHORT).show()
            Log.e("guardarRuta", "Faltan elevaciones")
            return
        }

        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        Log.d("guardarRuta", "Fecha actual: $currentDate")

        val puntos = currentPoints!!.mapIndexed { index, geoPoint ->
            Triple(geoPoint.latitude, geoPoint.longitude, routeElevations.getOrElse(index) { 0.0 })
        }
        Log.d("guardarRuta", "Puntos transformados: "+puntos.size+" puntos")

        val puntosInteres = currentWaypoints.orEmpty().map { waypointInfo ->
            Log.d("guardarRuta", "Procesando waypoint: ${waypointInfo.name}, userPhotoUrl: ${waypointInfo.userPhotoUrl}")
            val comentario = if (waypointInfo.name == waypointInfo.description) {
                waypointInfo.name
            } else {
                "${waypointInfo.name}\n${waypointInfo.description}"
            }
            DatabaseHelper.PuntoInteresData(
                0,
                waypointInfo.geoPoint.latitude,
                waypointInfo.geoPoint.longitude.toString(),
                comentario,
                waypointInfo.photoUrl,
                waypointInfo.userPhotoUrl
            )
        }
        Log.d("guardarRuta", "Puntos de interés transformados: "+puntosInteres.size+" puntos")

        val dbHelper = DatabaseHelper(requireContext())
        Log.d("guardarRuta", "DatabaseHelper inicializado")

        // Usar los tiempos reales de grabación
        val startTime = this.startTime
        val endTime = if (this.endTime > this.startTime) this.endTime else System.currentTimeMillis()
        Log.d("guardarRuta", "Tiempos: startTime=$startTime, endTime=$endTime")

        val rutaId = dbHelper.insertRoute(
            currentRouteName ?: "Ruta sin nombre",
            currentDate,
            puntos,
            startTime,
            endTime,
            puntosInteres,
            tipoRutaSeleccionado // Guardar el tipo de ruta seleccionado
        )
        Log.d("guardarRuta", "Ruta insertada, rutaId=$rutaId")

        if (rutaId != -1L) {
            Toast.makeText(requireContext(), "Ruta guardada en la base de datos", Toast.LENGTH_SHORT).show()
            Log.d("guardarRuta", "Ruta guardada con éxito")

            // Insignias ecológicas
            val rutasGuardadas = dbHelper.getAllRoutes()
            val fechaObtencion = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            // Variables para el diálogo
            var mensajeInsignia = ""
            var mensajeEcologico = ""

            // Semilla de Sendero
            if (rutasGuardadas.size == 1) {
                dbHelper.insertInsignia("Semilla de Sendero", fechaObtencion, rutaId, "Ecologica")
                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Semilla de Sendero!\n\n"
                mensajeEcologico = "¡Cada paso cuenta! Sigue explorando y cuidando nuestros senderos."
            }

            // Rutas Verdes
            when (rutasGuardadas.size) {
                10 -> {
                    dbHelper.insertInsignia("Rutas Verdes I", fechaObtencion, rutaId, "Ecologica")
                    mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Rutas Verdes I!\n\n"
                    mensajeEcologico = "¡Tu compromiso con las rutas verdes marca la diferencia! ¡Sigue así!"
                }
                25 -> {
                    dbHelper.insertInsignia("Rutas Verdes II", fechaObtencion, rutaId, "Ecologica")
                    mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Rutas Verdes II!\n\n"
                    mensajeEcologico = "¡Eres un verdadero defensor de la naturaleza! ¡Gracias por tu esfuerzo!"
                }
                50 -> {
                    dbHelper.insertInsignia("Rutas Verdes III", fechaObtencion, rutaId, "Ecologica")
                    mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Rutas Verdes III!\n\n"
                    mensajeEcologico = "¡Impresionante! ¡Tu pasión por la naturaleza es un ejemplo para todos!"
                }
            }

            // Cartógrafo Ecológico
            if (currentWaypoints != null && currentWaypoints!!.size >= 5) {
                dbHelper.insertInsignia("Cartógrafo Ecológico", fechaObtencion, rutaId, "Ecologica")
                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cartógrafo Ecológico!\n\n"
                mensajeEcologico = "¡Gracias por ayudarnos a mapear y proteger la naturaleza! ¡Tu contribución es invaluable!"
            }

            // Cronista de la Naturaleza
            val totalPuntosConInfoEcologica = dbHelper.obtenerTotalPuntosInteresConInfoEcologica()

            when (totalPuntosConInfoEcologica) {
                10 -> {
                    dbHelper.insertInsignia("Cronista de la Naturaleza I", fechaObtencion, rutaId, "Ecologica")
                    mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cronista de la Naturaleza I!\n\n"
                    mensajeEcologico = "¡Tus observaciones son valiosas para la conservación del entorno! ¡Sigue compartiendo tus conocimientos!"
                }
                25 -> {
                    dbHelper.insertInsignia("Cronista de la Naturaleza II", fechaObtencion, rutaId, "Ecologica")
                    mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cronista de la Naturaleza II!\n\n"
                    mensajeEcologico = "¡Eres un cronista excepcional! ¡Tu dedicación a la naturaleza es admirable!"
                }
                50 -> {
                    dbHelper.insertInsignia("Cronista de la Naturaleza III", fechaObtencion, rutaId, "Ecologica")
                    mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cronista de la Naturaleza III!\n\n"
                    mensajeEcologico = "¡Increíble! ¡Tu labor como cronista es fundamental para proteger nuestro planeta!"
                }
            }

            // Mostrar el diálogo si hay mensajes
            if (mensajeInsignia.isNotEmpty() && mensajeEcologico.isNotEmpty()) {
                mostrarDialogo(mensajeInsignia + mensajeEcologico)
            }

            clearRecordingDraftAfterSave()

        } else {
            Toast.makeText(requireContext(), "Error al guardar la ruta", Toast.LENGTH_SHORT).show()
            Log.e("guardarRuta", "Error al guardar la ruta")
        }
        Log.d("guardarRuta", "Fin de guardarRutaEnBaseDeDatos")
    }

    private fun mostrarDialogo(mensaje: String) {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_insignia, null)
        val insigniaMessage = dialogView.findViewById<TextView>(R.id.insignia_message)

        insigniaMessage.text = mensaje

        builder.setView(dialogView)
        builder.setPositiveButton("Aceptar") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    override fun onPause() {
        super.onPause()
        Log.d("HomeFragment", "onPause called")
        val segundos = (System.currentTimeMillis() - tiempoEntrada) / 1000
        dbHelper.registrarEvento("Inicio", "tiempo_en_pantalla", "${segundos}s")
        saveRouteData()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesActive = false
        if (requireActivity().isFinishing) {
            isAppClosing = true
            limpiarRecursos()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onStart() {
        super.onStart()
        if (!recordingReceiverRegistered) {
            ContextCompat.registerReceiver(
                requireContext(),
                recordingUpdatesReceiver,
                IntentFilter(MiServicio.ACTION_RECORDING_UPDATED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            recordingReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (recordingReceiverRegistered) {
            requireContext().unregisterReceiver(recordingUpdatesReceiver)
            recordingReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        tiempoEntrada = System.currentTimeMillis()
        Log.d("HomeFragment", "onResume called")
        isNavigating = false
        restoreRouteFromPrefs()
        requestNotificationPermission()
        loadRouteData()
        if (isRecording) {
            if (!isPaused) {
                // Centrar inmediatamente en la última ubicación conocida si existe
                lastLocation?.let { location ->
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    mapView.controller.setCenter(geoPoint)
                    mapView.controller.setZoom(17.0)
                }
                skipNextLocationAnimation = true
            }
            sendRecordingCommand(MiServicio.ACTION_ENSURE_RECORDING)
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissions()
            return
        }

        // Solo limpiar la polyline y datos de la grabación anterior, NO los overlays de la ruta cargada
        recordingPolyline?.let { mapView.overlays.remove(it) }
        recordingPolyline = null
        currentPoints = mutableListOf()
        currentWaypoints = mutableListOf() // Inicializa como lista vacía
        currentElevations = mutableListOf() // Inicializa como lista vacía
        startTime = System.currentTimeMillis()
        totalPauseTime = 0
        isRecording = true
        isPaused = false
        // Persistir el reinicio inmediatamente para que nunca reaparezcan puntos antiguos.
        saveRecordingPoints()
        saveRouteData()
        recordingPolyline = Polyline().apply {
            outlinePaint.color = Color.parseColor("#FF9800") // Naranja para la grabación
            outlinePaint.strokeWidth = 8f
        }
        mapView.overlays.add(recordingPolyline)
        mapView.invalidate()
        Toast.makeText(requireContext(), "Grabación iniciada", Toast.LENGTH_SHORT).show()
        updateUI(true)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesActive = false
        sendRecordingCommand(MiServicio.ACTION_START_RECORDING)
    }

    private fun stopRecording() {
        isRecording = false
        endTime = System.currentTimeMillis()
        val timeTaken = endTime - startTime - totalPauseTime
        if (timeTaken < 0 || startTime == 0L) {
            Toast.makeText(requireContext(), "Error: tiempo inválido (startTime: $startTime)", Toast.LENGTH_LONG).show()
            return
        }
        val seconds = timeTaken / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        Toast.makeText(requireContext(), "Tiempo total: $hours h $minutes min $remainingSeconds seg", Toast.LENGTH_SHORT).show()
        btnStartRoute.visibility = View.VISIBLE
        btnStopRoute.visibility = View.GONE
        btnSaveRoute.visibility = View.VISIBLE
        btnPauseRoute.visibility = View.GONE
        btnResumeRoute.visibility = View.GONE
        fusedLocationClient.removeLocationUpdates(locationCallback)
        // Deja el Polyline en el mapa tras detener
        saveRouteData()
        sendRecordingCommand(MiServicio.ACTION_STOP_RECORDING)
    }

    private fun pauseRoute() {
        if (isRecording && !isPaused) {
            isPaused = true
            pauseStartTime = System.currentTimeMillis()
            fusedLocationClient.removeLocationUpdates(locationCallback)
            saveRouteData()
            sendRecordingCommand(MiServicio.ACTION_PAUSE_RECORDING)
            updateUI(true)
            Toast.makeText(requireContext(), "Grabación pausada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeRoute() {
        if (isRecording && isPaused) {
            isPaused = false
            totalPauseTime += System.currentTimeMillis() - pauseStartTime
            saveRouteData()
            sendRecordingCommand(MiServicio.ACTION_RESUME_RECORDING)
            updateUI(true)
            Toast.makeText(requireContext(), "Grabación reanudada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addPointOfInterest() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Permiso de ubicación no concedido", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let { showAddPoiDialog(it.latitude, it.longitude) }
                ?: Toast.makeText(requireContext(), "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddPoiDialog(lat: Double, lon: Double) {
        val view = layoutInflater.inflate(R.layout.dialog_add_poi, null)
        val etComment = view.findViewById<EditText>(R.id.et_comment)
        val etPhotoUrl = view.findViewById<EditText>(R.id.et_photo_url)

        AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle("Agregar Punto de Interés")
            .setPositiveButton("Guardar") { _, _ ->
                val comment = etComment.text.toString()
                val photoUrl = etPhotoUrl.text.toString()
                drawPoiMarker(lat, lon, comment, photoUrl)
                // Añadir a currentWaypoints para que se guarde con la ruta
                if (currentWaypoints == null) currentWaypoints = mutableListOf()
                val waypoint = WaypointInfo(
                    geoPoint = GeoPoint(lat, lon),
                    name = comment.ifEmpty { "Punto de Interés" },
                    description = comment,
                    photoUrl = photoUrl
                )
                // Si la lista es inmutable, la convertimos a mutable
                if (currentWaypoints !is MutableList) {
                    currentWaypoints = currentWaypoints?.toMutableList() ?: mutableListOf()
                }
                (currentWaypoints as MutableList<WaypointInfo>).add(waypoint)
                saveRecordingWaypoints()
                Toast.makeText(requireContext(), "Punto de interés agregado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun drawPoiMarker(lat: Double, lon: Double, comment: String, photoUrl: String?) {
        val poiMarker = Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            title = "Punto de Interés"
            snippet = comment
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_poi)
            setOnMarkerClickListener { marker, _ ->
                showPoiDetailsDialog(marker.snippet, photoUrl)
                true
            }
        }
        mapView.overlayManager.add(poiMarker)
        mapView.invalidate()
    }

    private fun showPoiDetailsDialog(comment: String, photoUrl:String?) {
        val view = layoutInflater.inflate(R.layout.dialog_poi_details, null)
        val tvComment = view.findViewById<TextView>(R.id.tv_comment)
        val ivPhoto = view.findViewById<ImageView>(R.id.iv_photo)

        tvComment.text = comment ?: "Sin comentario"
        if (!photoUrl.isNullOrEmpty()) {
            ivPhoto.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(photoUrl))) }
            ivPhoto.setImageResource(R.drawable.ic_image_link)
        } else {
            ivPhoto.setImageResource(R.drawable.ic_no_image)
        }

        AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle("Detalles del Punto de Interés")
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun updateUI(recording: Boolean) {
        val btnAddPoi = view?.findViewById<Button?>(R.id.btn_add_poi)
        if (recording) {
            btnStartRoute.visibility = View.GONE
            btnStopRoute.visibility = View.VISIBLE
            btnPauseRoute.visibility = if (!isPaused) View.VISIBLE else View.GONE
            btnResumeRoute.visibility = if (isPaused) View.VISIBLE else View.GONE
            btnSaveRoute.visibility = View.GONE
            btnAddPoi?.visibility = View.VISIBLE
        } else {
            btnStartRoute.visibility = View.VISIBLE
            btnStopRoute.visibility = View.GONE
            btnPauseRoute.visibility = View.GONE
            btnResumeRoute.visibility = View.GONE
            // Una grabación detenida y restaurada sigue pendiente de guardar.
            btnSaveRoute.visibility = if ((currentPoints?.size ?: 0) > 1) View.VISIBLE else View.GONE
            btnAddPoi?.visibility = View.GONE
        }
    }

    private fun saveRouteData() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("isRecordingHome", isRecording)
        editor.putBoolean("isPausedHome", isPaused)
        editor.putLong("startTimeHome", startTime)
        editor.putLong("endTimeHome", endTime)
        editor.putLong("pauseStartTimeHome", pauseStartTime)
        editor.putLong("totalPauseTimeHome", totalPauseTime)
        editor.apply()
    }

    /** Limpia únicamente la grabación temporal después de guardarla definitivamente. */
    private fun clearRecordingDraftAfterSave() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesActive = false
        isRecording = false
        isPaused = false
        startTime = 0L
        endTime = 0L
        pauseStartTime = 0L
        totalPauseTime = 0L
        currentPoints = mutableListOf()
        currentWaypoints = mutableListOf()
        currentElevations = mutableListOf()
        currentRouteName = null
        currentDistance = 0.0

        recordingPolyline?.let { mapView.overlays.remove(it) }
        recordingPolyline = null
        mapView.overlays.removeAll { overlay ->
            overlay is Marker && overlay.title == "Punto de Interés"
        }
        mapView.invalidate()

        saveRecordingPoints()
        saveRouteData()
        updateUI(false)
    }

    private fun loadRouteData() {
        isRecording = sharedPreferences.getBoolean("isRecordingHome", false)
        isPaused = sharedPreferences.getBoolean("isPausedHome", false)
        startTime = sharedPreferences.getLong("startTimeHome", 0)
        endTime = sharedPreferences.getLong("endTimeHome", 0)
        pauseStartTime = sharedPreferences.getLong("pauseStartTimeHome", 0)
        totalPauseTime = sharedPreferences.getLong("totalPauseTimeHome", 0)
        updateUI(isRecording)
    }

    private fun restoreRouteFromPrefs() {
        Log.d("HomeFragment", "restoreRouteFromPrefs called")
        val routePointsJson = sharedPreferences.getString(RUTA_POINTS_KEY, null)
        val waypointsJson = sharedPreferences.getString(WAYPOINTS_KEY, null)
        val elevationsJson = sharedPreferences.getString(ELEVATIONS_KEY, null)
        val routeNameJson = sharedPreferences.getString(CURRENT_ROUTE_NAME_KEY, null)
        // Restaurar puntos y elevaciones de grabación
        restoreRecordingPoints()

        val recordingIsActive = sharedPreferences.getBoolean("isRecordingHome", false)
        val recordingPointCount = currentPoints?.size ?: 0
        // Un punto aislado de una grabación ya detenida no constituye una ruta pendiente.
        val hasRecordingPoints = recordingPointCount > 1 ||
            (recordingIsActive && recordingPointCount > 0)

        if (routePointsJson != null && waypointsJson != null && elevationsJson != null && routeNameJson != null) {
            val pointsType = object : TypeToken<List<GeoPoint>>() {}.type
            val waypointsType = object : TypeToken<List<WaypointInfo>>() {}.type
            val elevationsType = object : TypeToken<List<Double>>() {}.type
            val points: List<GeoPoint> = gson.fromJson(routePointsJson, pointsType)
            val waypoints: List<WaypointInfo> = gson.fromJson(waypointsJson, waypointsType)
            val elevations: List<Double> = gson.fromJson(elevationsJson, elevationsType)
            val routeName: String? = sharedPreferences.getString(CURRENT_ROUTE_NAME_KEY, null)
            Log.d("HomeFragment", "points: $points")
            Log.d("HomeFragment", "waypoints: $waypoints")
            Log.d("HomeFragment", "elevations: $elevations")
            Log.d("HomeFragment", "routeName: $routeName")

            // Restaurar la polyline de grabación independientemente de la ruta GPX cargada.
            if (hasRecordingPoints) {
                recordingPolyline?.let { mapView.overlays.remove(it) }
                recordingPolyline = Polyline().apply {
                    outlinePaint.color = Color.parseColor("#FF9800")
                    outlinePaint.strokeWidth = 8f
                }
                recordingPolyline?.setPoints(currentPoints)
                mapView.overlays.add(recordingPolyline)
                // Centrar el mapa en el último punto grabado y restaurar zoom
                val lastPoint = currentPoints!!.last()
                mapView.controller.setCenter(lastPoint)
                mapView.controller.setZoom(17.0)
                recalculateDistanceFromPoints()
            } else {
                recordingPolyline?.let { mapView.overlays.remove(it) }
                recordingPolyline = null
                currentDistance = 0.0
            }
            if (!hasRecordingPoints) {
                currentWaypoints = waypoints
                currentElevations = elevations.toMutableList()
                currentRouteName = routeName
            }
            drawGpxRoute(points, waypoints)
        } else {
            // No hay GPX cargado: limpiar únicamente sus overlays. La grabación naranja
            // tiene su propia persistencia y debe sobrevivir al volver a esta pantalla.
            polyline?.let { mapView.overlays.remove(it) }
            polyline = null
            mapView.overlays.removeAll(gpxOverlays.toList())
            gpxOverlays.clear()

            if (hasRecordingPoints) {
                if (recordingPolyline == null) {
                    recordingPolyline = Polyline().apply {
                        outlinePaint.color = Color.parseColor("#FF9800")
                        outlinePaint.strokeWidth = 8f
                    }
                    mapView.overlays.add(recordingPolyline)
                }
                recordingPolyline?.setPoints(currentPoints)
                recalculateDistanceFromPoints()
                mapView.controller.setCenter(currentPoints!!.last())
                mapView.controller.setZoom(17.0)
            } else {
                recordingPolyline?.let { mapView.overlays.remove(it) }
                recordingPolyline = null
                currentDistance = 0.0
                showSpainOverview()
            }
            mapView.invalidate()
        }
    }

    /** Muestra la península, Baleares y Canarias cuando no hay una ruta que encuadrar. */
    private fun showSpainOverview() {
        mapView.controller.setZoom(5.0)
        mapView.controller.setCenter(GeoPoint(36.5, -6.5))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permiso ya concedido
                // Continúa con la lógica de notificación
            } else {
                // Solicitar permiso
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        } else {
            // Versiones anteriores a Android 13, no se necesita permiso
            // Continúa con la lógica de notificación
        }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }

    // Variable para guardar el tipo de ruta seleccionado temporalmente
    private var tipoRutaSeleccionado: String = "Otro"

    // Muestra un diálogo para introducir el nombre de la ruta antes de guardar
    private fun mostrarDialogoGuardarRuta() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_route, null)
        val etRouteName = dialogView.findViewById<EditText>(R.id.et_route_name)
        val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_route_type)
        val tiposRuta = listOf("Ciclismo", "Senderismo", "Motociclismo", "Coche", "Otro")
        val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, tiposRuta)
        spinner.adapter = adapter

        builder.setView(dialogView)
        builder.setTitle("Guardar ruta")
        builder.setPositiveButton("Guardar") { dialog, _ ->
            val nombreRuta = etRouteName.text.toString().trim()
            val tipoRuta = spinner.selectedItem?.toString() ?: "Otro"
            if (nombreRuta.isEmpty()) {
                Toast.makeText(requireContext(), "Por favor, introduce un nombre para la ruta", Toast.LENGTH_SHORT).show()
            } else {
                currentRouteName = nombreRuta
                tipoRutaSeleccionado = tipoRuta
                guardarRutaEnBaseDeDatos()
                dialog.dismiss()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }

    fun onNavigationAttempt(listener: NavigationListener, fragment: Fragment) {
        listener.navigateToFragment(fragment)
    }

    interface NavigationListener {
        fun navigateToFragment(fragment: Fragment)
    }
}
