package com.example.gpxeditor.view.fragments
// Archivo eliminado. Fragmento obsoleto.
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.gpxeditor.controller.CreateViewModel
import com.example.gpxeditor.R
import com.example.gpxeditor.model.database.DatabaseHelper
import com.example.gpxeditor.model.entities.PuntoInteresTemporal
import com.google.android.gms.location.*
import com.google.gson.Gson
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

class CreateFragment : Fragment(R.layout.fragment_create) {
    private lateinit var mapView: MapView
    private lateinit var btnStartRoute: Button
    private lateinit var btnStopRoute: Button
    private lateinit var btnSaveRoute: Button
    private lateinit var btnRecomendaciones: Button
    private lateinit var routeLine: Polyline
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var userMarker: Marker? = null
    private lateinit var btnAddPoi: Button
    private lateinit var databaseHelper: DatabaseHelper
    private var currentRouteId: Long = -1L
    private var routeSaved: Boolean = false
    private var isNavigating = false
    private lateinit var viewModel: CreateViewModel
    private var isRecording: Boolean = false
    private var startTime: Long = 0
    private var endTime: Long = 0
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private var isPaused: Boolean = false
    private var pauseStartTime: Long = 0
    private var totalPauseTime: Long = 0
    private lateinit var btnPauseRoute: Button
    private lateinit var btnResumeRoute: Button
    private var tiempoEntrada: Long = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            android.util.Log.d("CreateFragment", "Iniciando onViewCreated")
            sharedPreferences = requireContext().getSharedPreferences("ruta_data", Context.MODE_PRIVATE)
            Configuration.getInstance().load(requireContext(), requireActivity().getPreferences(0))

            mapView = view.findViewById(R.id.mapView)
            if (mapView == null) {
                android.util.Log.e("CreateFragment", "mapView es null")
                Toast.makeText(requireContext(), "Error: no se pudo cargar el mapa", Toast.LENGTH_LONG).show()
                return
            }
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)

            val mapController = mapView.controller
            val startPoint = GeoPoint(40.4168, -3.7038)
            mapController.setZoom(12.0)
            mapController.setCenter(startPoint)
            android.util.Log.d("CreateFragment", "Mapa inicializado correctamente")
        } catch (e: Exception) {
            android.util.Log.e("CreateFragment", "Error al inicializar el mapa", e)
            Toast.makeText(requireContext(), "Error al inicializar el mapa: " + e.localizedMessage, Toast.LENGTH_LONG).show()
            return
        }

        btnStartRoute = view.findViewById(R.id.btnStartRoute)
        btnStopRoute = view.findViewById(R.id.btnStopRoute)
        btnSaveRoute = view.findViewById(R.id.btnSaveRoute)
        btnAddPoi = view.findViewById(R.id.btn_add_poi)
        btnPauseRoute = view.findViewById(R.id.btnPauseRoute)
        btnResumeRoute = view.findViewById(R.id.btnResumeRoute)

        btnStartRoute.setOnClickListener {
            databaseHelper.registrarEvento("Grabacion", "iniciar_grabacion", null)
            startRecording()
        }
        btnStopRoute.setOnClickListener {
            databaseHelper.registrarEvento("Grabacion", "parar_grabacion", null)
            stopRecording()
        }
        btnSaveRoute.setOnClickListener {
            saveRoute()
        }
        btnAddPoi.setOnClickListener {
            databaseHelper.registrarEvento("Grabacion", "click_añadir_poi", null)
            addPointOfInterest()
        }
        btnPauseRoute.setOnClickListener {
            databaseHelper.registrarEvento("Grabacion", "pausar_grabacion", null)
            pauseRoute()
        }
        btnResumeRoute.setOnClickListener {
            databaseHelper.registrarEvento("Grabacion", "reanudar_grabacion", null)
            resumeRoute()
        }

        routeLine = Polyline().apply { width = 5f }
        mapView.overlays.add(routeLine)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (viewModel.isRecording.value == true && !isPaused) {
                    locationResult.lastLocation?.let { addLocationToRoute(it) }
                }
            }
        }

        centerMapOnUserLocation()
        databaseHelper = DatabaseHelper(requireContext())

        viewModel = ViewModelProvider(this).get(CreateViewModel::class.java)

        viewModel.isRecording.observe(viewLifecycleOwner) { recording ->
            isRecording = recording
            updateUI(recording)
        }

        viewModel.startTime.observe(viewLifecycleOwner) { time ->
            startTime = time
        }

        viewModel.routePoints.observe(viewLifecycleOwner) { points ->
            updateRouteLine(points)
        }

        viewModel.puntosInteresTemporales.observe(viewLifecycleOwner) { puntos ->
            redrawPuntosInteres(puntos)
        }

        loadRouteData()
    }

    override fun onPause() {
        super.onPause()
        val segundos = (System.currentTimeMillis() - tiempoEntrada) / 1000
        databaseHelper.registrarEvento("Grabacion", "tiempo_en_pantalla", "${segundos}s")
        saveRouteData()
    }

    override fun onResume() {
        super.onResume()
        tiempoEntrada = System.currentTimeMillis()
        loadRouteData()
        if (isRecording && !isPaused) {
            requestLocationUpdates()
        }
        requestNotificationPermission() // Llamar aquí para solicitar el permiso
    }

    private fun saveRouteData() {
        val editor = sharedPreferences.edit()
        editor.putBoolean("isRecording", isRecording)
        editor.putString("routePoints", gson.toJson(viewModel.routePoints.value))
        editor.putString("poiPoints", gson.toJson(viewModel.puntosInteresTemporales.value))
        editor.putBoolean("isPaused", isPaused)
        editor.putLong("pauseStartTime", pauseStartTime)
        editor.putLong("totalPauseTime", totalPauseTime)
        editor.apply()
    }

    private fun loadRouteData() {
        isRecording = sharedPreferences.getBoolean("isRecording", false)
        val routePointsJson = sharedPreferences.getString("routePoints", null)
        val poiPointsJson = sharedPreferences.getString("poiPoints", null)

        if (routePointsJson != null) {
            val routePoints = gson.fromJson(routePointsJson, Array<GeoPoint>::class.java)?.toMutableList() ?: mutableListOf()
            viewModel.routePoints.value = routePoints
        } else {
            viewModel.routePoints.value = mutableListOf()
        }

        if (poiPointsJson != null) {
            val poiPoints = gson.fromJson(poiPointsJson, Array<PuntoInteresTemporal>::class.java)?.toMutableList() ?: mutableListOf()
            viewModel.puntosInteresTemporales.value = poiPoints
        } else {
            viewModel.puntosInteresTemporales.value = mutableListOf()
        }
        isPaused = sharedPreferences.getBoolean("isPaused", false)
        pauseStartTime = sharedPreferences.getLong("pauseStartTime", 0)
        totalPauseTime = sharedPreferences.getLong("totalPauseTime", 0)
        updateUI(isRecording)
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

        AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle("Agregar Punto de Interés")
            .setPositiveButton("Guardar") { _, _ ->
                val comment = etComment.text.toString()
                viewModel.addPuntoInteres(PuntoInteresTemporal(lat, lon, comment, null, null))
                drawPoiMarker(lat, lon, comment, null)
                // Insignia Cronista de la Naturaleza
                val waypointsConInfoEcologica = viewModel.puntosInteresTemporales.value?.filter { it.imagenPath?.isNotEmpty() == true }?.size ?: 0
                val fechaObtencion = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                when (waypointsConInfoEcologica) {
                    10 -> databaseHelper.insertInsignia("Cronista de la Naturaleza I", fechaObtencion, null, "Ecologica")
                    25 -> databaseHelper.insertInsignia("Cronista de la Naturaleza II", fechaObtencion, null, "Ecologica")
                    50 -> databaseHelper.insertInsignia("Cronista de la Naturaleza III", fechaObtencion, null, "Ecologica")
                }

                Toast.makeText(requireContext(), "Punto de interés agregado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun drawPoiMarker(lat: Double, lon: Double, comment: String, photoUrl: String?) {
        val poiMarker = Marker(mapView).apply {
            position = GeoPoint(lat, lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Punto de Interés"
            snippet = comment
            val markerIcon = if (photoUrl.isNullOrBlank()) {
                R.drawable.ic_poi
            } else {
                R.drawable.ic_poi_photo
            }
            icon = ContextCompat.getDrawable(requireContext(), markerIcon)
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

    private fun startRecording() {
        startTime = System.currentTimeMillis() // Establece el tiempo de inicio
        totalPauseTime = 0 // Reinicia el tiempo de pausa
        viewModel.startRecording()
        requestLocationUpdates()
    }

    private fun stopRecording() {
        viewModel.stopRecording()
        endTime = System.currentTimeMillis()
        val timeTaken = endTime - startTime - totalPauseTime

        // Validación para evitar resultados absurdos
        if (timeTaken < 0 || startTime == 0L) {
            Toast.makeText(requireContext(), "Error: tiempo inválido (startTime: $startTime)", Toast.LENGTH_LONG).show()
            return
        }

        val seconds = timeTaken / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        // Depuración para ver los valores
        Toast.makeText(requireContext(), "start: $startTime, end: $endTime, pause: $totalPauseTime", Toast.LENGTH_LONG).show()
        Toast.makeText(requireContext(), "timeTaken: $timeTaken ms", Toast.LENGTH_LONG).show()
        Toast.makeText(requireContext(), "Tiempo total: $hours h $minutes min $remainingSeconds seg", Toast.LENGTH_SHORT).show()

        btnStartRoute.visibility = View.VISIBLE
        btnStopRoute.visibility = View.GONE
        btnSaveRoute.visibility = View.VISIBLE
        btnPauseRoute.visibility = View.GONE
        btnResumeRoute.visibility = View.GONE
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun pauseRoute() {
        if (viewModel.isRecording.value == true && !isPaused) {
            isPaused = true
            pauseStartTime = System.currentTimeMillis()
            fusedLocationClient.removeLocationUpdates(locationCallback)
            updateUI(true)
        }
    }

    private fun resumeRoute() {
        if (viewModel.isRecording.value == true && isPaused) {
            isPaused = false
            totalPauseTime += System.currentTimeMillis() - pauseStartTime
            requestLocationUpdates()
            updateUI(true)
        }
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000).build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun centerMapOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val userLocation = location?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(40.4168, -3.7038)
            mapView.controller.setZoom(18.0)
            mapView.controller.setCenter(userLocation)
            updateUserMarker(userLocation)
        }.addOnFailureListener {
            val madridLocation = GeoPoint(40.4168, -3.7038)
            mapView.controller.setZoom(18.0)
            mapView.controller.setCenter(madridLocation)
            updateUserMarker(madridLocation)
        }
    }

    private fun addLocationToRoute(location: Location) {
        val newPoint = GeoPoint(location.latitude, location.longitude, location.altitude)
        viewModel.addRoutePoint(newPoint)
        mapView.controller.setCenter(newPoint)
        updateUserMarker(newPoint)
    }

    private fun updateUserMarker(location: GeoPoint) {
        if (userMarker == null) {
            userMarker = Marker(mapView).apply {
                position = location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_blue_dot)
            }
            mapView.overlays.add(userMarker)
        } else {
            userMarker?.position = location
        }
        mapView.invalidate()
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private fun convertRoutePoints(routePoints: List<GeoPoint>): List<Triple<Double, Double, Double>> =
        routePoints.map { Triple(it.latitude, it.longitude, it.altitude) }

    private fun saveRoute() {
        val routePoints = viewModel.routePoints.value ?: emptyList()
        if (routePoints.isEmpty()) {
            Toast.makeText(requireContext(), "No hay puntos en la ruta", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_save_route, null)
        val input = dialogView.findViewById<EditText>(R.id.et_route_name)
        val routeTypeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_route_type)

        val routeTypes = resources.getStringArray(R.array.route_types)
        val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, routeTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        routeTypeSpinner.adapter = adapter

        AlertDialog.Builder(requireContext())
            .setTitle("Guardar ruta")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val routeName = input.text.toString().trim()
                val selectedRouteType = routeTypeSpinner.selectedItem.toString()

                if (routeName.isEmpty()) {
                    Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                } else {
                    databaseHelper.registrarEvento("Grabacion", "guardar_ruta", routeName)
                    val fechaActual = getCurrentDate()
                    val routePointsConverted = convertRoutePoints(routePoints)
                    val nuevaRutaId = databaseHelper.insertRoute(
                        routeName, fechaActual, routePointsConverted, startTime, endTime, tipoRuta = selectedRouteType
                    )
                    if (nuevaRutaId != -1L) {
                        currentRouteId = nuevaRutaId
                        routeSaved = true
                        viewModel.puntosInteresTemporales.value?.forEach { punto ->
                            databaseHelper.insertPuntoInteres(
                                currentRouteId, punto.latitud, punto.longitud, punto.comentario, punto.imagenPath ?: "", punto.userImagePath ?: ""
                            )
                        }
                        viewModel.puntosInteresTemporales.value?.clear()
                        Toast.makeText(requireContext(), "Ruta guardada como: $routeName ($selectedRouteType)", Toast.LENGTH_SHORT).show()

                        // Insignias ecológicas
                        val rutasGuardadas = databaseHelper.getAllRoutes()
                        val fechaObtencion = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                        // Variables para el diálogo
                        var mensajeInsignia = ""
                        var mensajeEcologico = ""

                        // Semilla de Sendero
                        if (rutasGuardadas.size == 1) {
                            databaseHelper.insertInsignia("Semilla de Sendero", fechaObtencion, nuevaRutaId, "Ecologica")
                            mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Semilla de Sendero!\n\n"
                            mensajeEcologico = "¡Cada paso cuenta! Sigue explorando y cuidando nuestros senderos."
                        }

                        // Rutas Verdes
                        when (rutasGuardadas.size) {
                            10 -> {
                                databaseHelper.insertInsignia("Rutas Verdes I", fechaObtencion, nuevaRutaId, "Ecologica")
                                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Rutas Verdes I!\n\n"
                                mensajeEcologico = "¡Tu compromiso con las rutas verdes marca la diferencia! ¡Sigue así!"
                            }
                            25 -> {
                                databaseHelper.insertInsignia("Rutas Verdes II", fechaObtencion, nuevaRutaId, "Ecologica")
                                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Rutas Verdes II!\n\n"
                                mensajeEcologico = "¡Eres un verdadero defensor de la naturaleza! ¡Gracias por tu esfuerzo!"
                            }
                            50 -> {
                                databaseHelper.insertInsignia("Rutas Verdes III", fechaObtencion, nuevaRutaId, "Ecologica")
                                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Rutas Verdes III!\n\n"
                                mensajeEcologico = "¡Impresionante! ¡Tu pasión por la naturaleza es un ejemplo para todos!"
                            }
                        }

                        // Cartógrafo Ecológico
                        if (viewModel.puntosInteresTemporales.value?.size ?: 0 >= 5) {
                            databaseHelper.insertInsignia("Cartógrafo Ecológico", fechaObtencion, nuevaRutaId, "Ecologica")
                            mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cartógrafo Ecológico!\n\n"
                            mensajeEcologico = "¡Gracias por ayudarnos a mapear y proteger la naturaleza! ¡Tu contribución es invaluable!"
                        }

                        // Cronista de la Naturaleza
                        val totalPuntosConInfoEcologica = databaseHelper.obtenerTotalPuntosInteresConInfoEcologica()

                        when (totalPuntosConInfoEcologica) {
                            10 -> {
                                databaseHelper.insertInsignia("Cronista de la Naturaleza I", fechaObtencion, nuevaRutaId, "Ecologica")
                                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cronista de la Naturaleza I!\n\n"
                                mensajeEcologico = "¡Tus observaciones son valiosas para la conservación del entorno! ¡Sigue compartiendo tus conocimientos!"
                            }
                            25 -> {
                                databaseHelper.insertInsignia("Cronista de la Naturaleza II", fechaObtencion, nuevaRutaId, "Ecologica")
                                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cronista de la Naturaleza II!\n\n"
                                mensajeEcologico = "¡Eres un cronista excepcional! ¡Tu dedicación a la naturaleza es admirable!"
                            }
                            50 -> {
                                databaseHelper.insertInsignia("Cronista de la Naturaleza III", fechaObtencion, nuevaRutaId, "Ecologica")
                                mensajeInsignia = "¡Felicidades! ¡Has obtenido la insignia: Cronista de la Naturaleza III!\n\n"
                                mensajeEcologico = "¡Increíble! ¡Tu labor como cronista es fundamental para proteger nuestro planeta!"
                            }
                        }

                        // Mostrar el diálogo si hay mensajes
                        if (mensajeInsignia.isNotEmpty() && mensajeEcologico.isNotEmpty()) {
                            mostrarDialogo(mensajeInsignia + mensajeEcologico)
                        }

                    } else {
                        Toast.makeText(requireContext(), "Error al guardar la ruta", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
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

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
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
    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }

    private fun mostrarDialogoConfirmacionDetenerRuta(onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("¿Detener la ruta?")
            .setMessage("¿Estás seguro de que quieres detener la ruta sin guardar?")
            .setPositiveButton("Sí") { _, _ ->
                stopRecording()
                onConfirm.invoke()
            }
            .setNegativeButton("No", null)
            .show()
    }

    fun onNavigationAttempt(listener: HomeFragment.NavigationListener, fragment: Fragment) {
        isNavigating = true
        if (isRecording) {
            mostrarDialogoConfirmacionDetenerRuta {
                listener.navigateToFragment(fragment)
                isNavigating = false
            }
        } else {
            listener.navigateToFragment(fragment)
            isNavigating = false
        }
    }

    private fun updateUI(recording: Boolean) {
        if (recording) {
            btnStartRoute.visibility = View.GONE
            btnStopRoute.visibility = View.VISIBLE
            btnSaveRoute.visibility = View.GONE
            btnPauseRoute.visibility = if (isPaused) View.GONE else View.VISIBLE
            btnResumeRoute.visibility = if (isPaused) View.VISIBLE else View.GONE
        } else {
            btnStartRoute.visibility = View.VISIBLE
            btnStopRoute.visibility = View.GONE
            btnSaveRoute.visibility = View.VISIBLE
            btnPauseRoute.visibility = View.GONE
            btnResumeRoute.visibility = View.GONE
        }
    }

    private fun updateRouteLine(points: List<GeoPoint>) {
        routeLine.setPoints(points)
        mapView.invalidate()
    }


    private fun redrawPuntosInteres(puntos: List<PuntoInteresTemporal>) {
        mapView.overlays.removeAll { it is Marker && it.title == "Punto de Interés" }
        puntos.forEach { punto ->
            drawPoiMarker(punto.latitud, punto.longitud, punto.comentario, punto.imagenPath)
        }
        mapView.invalidate()
    }
}
