package com.example.gpxeditor.controller

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.gpxeditor.R
import com.example.gpxeditor.util.PoiPhotoStorage
import com.example.gpxeditor.model.database.DatabaseHelper
import com.example.gpxeditor.model.entities.PuntoInteres
import com.example.gpxeditor.model.entities.Route
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.pow
import kotlin.math.sqrt

class RouteDetailActivity : AppCompatActivity() {
    private lateinit var route: Route
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var statsTextView: TextView
    private lateinit var routeMapView: MapView
    private lateinit var deleteButton: ImageButton
    private lateinit var routeNameTypeTextView: TextView
    private lateinit var insigniasButton: Button
    private lateinit var insigniasDesbloqueadasLayout: LinearLayout
    private lateinit var detailScrollView: ScrollView
    private lateinit var detailContent: RelativeLayout
    private lateinit var mapFullscreenButton: ImageButton
    private var isMapFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            load(applicationContext, getSharedPreferences(packageName, MODE_PRIVATE))
            userAgentValue = "NatuRutas/1.0 (Android; com.example.gpxeditor)"
        }

        setContentView(R.layout.activity_route_detail)

        val routeId = intent.getLongExtra("route_id", -1L)
        Log.d("RouteDetailActivity", "onCreate: routeId = $routeId")

        dbHelper = DatabaseHelper(this)
        val routeNullable = dbHelper.getRouteById(routeId)

        if (routeNullable != null) {
            route = routeNullable
            statsTextView = findViewById(R.id.statsTextView)
            routeMapView = findViewById(R.id.routeMapView)
            val cartoVoyager = XYTileSource(
                "CARTO Voyager", 0, 20, 256, ".png",
                arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager/"),
                "© OpenStreetMap contributors, © CARTO"
            )
            routeMapView.setTileSource(cartoVoyager)
            routeMapView.setMultiTouchControls(true)
            routeMapView.overlays.add(CopyrightOverlay(this))
            deleteButton = findViewById(R.id.deleteButton)
            routeNameTypeTextView = findViewById(R.id.routeNameTypeTextView)
            insigniasButton = findViewById(R.id.insigniasButton)
            insigniasDesbloqueadasLayout = findViewById(R.id.insigniasDesbloqueadasLayout)
            detailScrollView = findViewById(R.id.detailScrollView)
            detailContent = findViewById(R.id.detailContent)
            mapFullscreenButton = findViewById(R.id.mapFullscreenButton)
            routeNameTypeTextView.text = "${route.name} (${route.tipoRuta})"

            val points = dbHelper.getRoutePoints(routeId)
            drawRouteOnMap(points)

            val pois = dbHelper.getPuntosInteresByRouteId(routeId)
            Log.d("RouteDetailActivity", "onCreate: POIs recuperados de la base de datos. Tamaño: ${pois.size}")
            drawPoisOnMap(pois)

            if (points.isNotEmpty()) {
                routeMapView.controller.setCenter(points[0])
                routeMapView.controller.setZoom(15.0)
            }

            val distance = calculateDistance(points)
            val duration = calculateDuration(route.id)
            statsTextView.text = "Distancia: ${formatDistance(distance)} km\nDuración: ${formatDuration(duration)} min"

            deleteButton.setOnClickListener {
                showDeleteConfirmationDialog(routeId)
            }

            insigniasButton.setOnClickListener {
                mostrarMenuInsignias()
            }

            mapFullscreenButton.setOnClickListener {
                setMapFullscreen(!isMapFullscreen)
            }

            mostrarInsigniasDesbloqueadas(routeId)
        } else {
            Toast.makeText(this, "Error al cargar la ruta", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setMapFullscreen(fullscreen: Boolean) {
        isMapFullscreen = fullscreen
        val detailVisibility = if (fullscreen) View.GONE else View.VISIBLE
        routeNameTypeTextView.visibility = detailVisibility
        statsTextView.visibility = detailVisibility
        insigniasButton.visibility = detailVisibility
        insigniasDesbloqueadasLayout.visibility = detailVisibility
        deleteButton.visibility = detailVisibility

        val padding = if (fullscreen) 0 else (16 * resources.displayMetrics.density).toInt()
        detailContent.setPadding(padding, padding, padding, padding)
        mapFullscreenButton.setImageResource(
            if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
        mapFullscreenButton.contentDescription = getString(
            if (fullscreen) R.string.collapse_map else R.string.expand_map
        )

        detailScrollView.post {
            val params = routeMapView.layoutParams as RelativeLayout.LayoutParams
            params.height = if (fullscreen) {
                detailScrollView.height
            } else {
                (250 * resources.displayMetrics.density).toInt()
            }
            if (fullscreen) {
                params.removeRule(RelativeLayout.BELOW)
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            } else {
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
                params.addRule(RelativeLayout.BELOW, R.id.statsTextView)
            }
            routeMapView.layoutParams = params
            routeMapView.invalidate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (isMapFullscreen) {
            setMapFullscreen(false)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::routeMapView.isInitialized) routeMapView.onResume()
    }

    override fun onPause() {
        if (::routeMapView.isInitialized) routeMapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::routeMapView.isInitialized) routeMapView.onDetach()
        if (::dbHelper.isInitialized) dbHelper.close()
        super.onDestroy()
    }

    private fun mostrarMenuInsignias() {
        val nombresInsignias = listOf(
            "Semilla de Sendero",
            "Rutas Verdes I",
            "Rutas Verdes II",
            "Rutas Verdes III",
            "Cartógrafo Ecológico",
            "Cronista de la Naturaleza I",
            "Cronista de la Naturaleza II",
            "Cronista de la Naturaleza III"
        )

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Insignias (¡Haz click y aprende a desbloquearlas!)")

        val items = nombresInsignias.toTypedArray()
        builder.setItems(items) { _, which ->
            val nombreInsignia = items[which]
            mostrarDialogoExplicacionInsignia(nombreInsignia)
        }

        builder.show()
    }

    private fun mostrarInsigniasDesbloqueadas(routeId: Long) {
        val insignias = dbHelper.getInsignias().filter { it.rutaId == routeId }

        insigniasDesbloqueadasLayout.removeAllViews() // Limpiar vistas anteriores

        if (insignias.isNotEmpty()) {
            insignias.forEach { insignia ->
                val textView = TextView(this).apply {
                    text = insignia.nombre
                    textSize = 16f
                    setPadding(8, 8, 8, 8)
                }
                insigniasDesbloqueadasLayout.addView(textView)
            }
        } else {
            val textView = TextView(this).apply {
                text = "No hay insignias desbloqueadas para esta ruta."
                textSize = 16f
                setPadding(8, 8, 8, 8)
            }
            insigniasDesbloqueadasLayout.addView(textView)
        }
    }

    private fun mostrarDialogoExplicacionInsignia(nombreInsignia: String) {
        val dialogo = AlertDialog.Builder(this)
            .setTitle(nombreInsignia)
            .setMessage(obtenerExplicacionInsignia(nombreInsignia))
            .setPositiveButton("Cerrar", null)
            .create()

        dialogo.show()
    }

    private fun obtenerExplicacionInsignia(nombreInsignia: String): String {
        return when (nombreInsignia) {
            "Semilla de Sendero" -> "Guarda tu primera ruta."
            "Rutas Verdes I" -> "Guarda 10 rutas."
            "Rutas Verdes II" -> "Guarda 25 rutas."
            "Rutas Verdes III" -> "Guarda 50 rutas."
            "Cartógrafo Ecológico" -> "Guarda una ruta con al menos 5 puntos de interés con información ecológica."
            "Cronista de la Naturaleza I" -> "Añade fotos y comentarios a 10 puntos de interés."
            "Cronista de la Naturaleza II" -> "Añade fotos y comentarios a 25 puntos de interés."
            "Cronista de la Naturaleza III" -> "Añade fotos y comentarios a 50 puntos de interés."
            else -> "No hay explicación disponible."
        }
    }

    private fun showDeleteConfirmationDialog(routeId: Long) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Ruta")
            .setMessage("¿Estás seguro de que quieres eliminar esta ruta?")
            .setPositiveButton("Sí") { _, _ ->
                val rowsDeleted = dbHelper.deleteRoute(routeId)
                if (rowsDeleted > 0) {
                    Toast.makeText(this, "Ruta eliminada", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Error al eliminar la ruta", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun drawRouteOnMap(points: List<GeoPoint>) {
        val polyline = Polyline().apply {
            setPoints(points)
            outlinePaint.color = ContextCompat.getColor(
                this@RouteDetailActivity,
                R.color.naturutas_primary
            )
            outlinePaint.strokeWidth = 8f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
        }
        routeMapView.overlays.add(polyline)

        if (points.isNotEmpty()) {
            val startDrawable = ContextCompat.getDrawable(this, R.drawable.ic_route_start)
            val startMarker = Marker(routeMapView)
            startMarker.position = points.first()
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            startMarker.title = "Inicio de la ruta"
            startMarker.icon = startDrawable
            routeMapView.overlays.add(startMarker)

            val endDrawable = ContextCompat.getDrawable(this, R.drawable.ic_route_finish)
            val endMarker = Marker(routeMapView)
            endMarker.position = points.last()
            endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            endMarker.title = "Fin de la ruta"
            endMarker.icon = endDrawable
            routeMapView.overlays.add(endMarker)
        }
    }

    private fun drawPoisOnMap(pois: List<PuntoInteres>) {
        Log.d("RouteDetailActivity", "drawPoisOnMap: Lista de POIs recibida. Tamaño: ${pois.size}")
        if (pois.isEmpty()) {
            Log.d("RouteDetailActivity", "drawPoisOnMap: La lista de POIs está vacía.")
            return
        }

        pois.forEach { poi ->
            Log.d("RouteDetailActivity", "drawPoisOnMap: Creando marcador para POI: ${poi.comentario}")

            if (poi.latitud == 0.0 || poi.longitud == 0.0) {
                Log.e("RouteDetailActivity", "drawPoisOnMap: Latitud o longitud inválida para POI: ${poi.comentario}")
                return@forEach
            }

            val marker = Marker(routeMapView).apply {
                position = GeoPoint(poi.latitud, poi.longitud)
                title = "Punto de Interés"
                snippet = poi.comentario

                val markerIcon = if (
                    poi.userImagenUrl.isNullOrBlank() && poi.imagenUrl.isNullOrBlank()
                ) {
                    R.drawable.ic_poi
                } else {
                    R.drawable.ic_poi_photo
                }
                val iconDrawable =
                    ContextCompat.getDrawable(this@RouteDetailActivity, markerIcon)
                icon = iconDrawable ?: run {
                    Log.e(
                        "RouteDetailActivity",
                        "drawPoisOnMap: Icono no encontrado, usando predeterminado"
                    )
                    ContextCompat.getDrawable(
                        this@RouteDetailActivity,
                        org.osmdroid.library.R.drawable.marker_default
                    )
                }

                setOnMarkerClickListener { _, _ ->
                    Log.d(
                        "RouteDetailActivity",
                        "drawPoisOnMap: Marcador clickeado: ${poi.comentario}"
                    )
                    showPoiDetailsDialog(
                        poi.comentario,
                        poi.imagenUrl, // Usar poi.imagenUrl
                        poi.userImagenUrl, // Usar poi.userImagenUrl
                        poi.id // Pasar el poiId
                    )
                    true
                }
            }

            try {
                routeMapView.overlays.add(marker)
            } catch (e: Exception) {
                Log.e("RouteDetailActivity", "Error al añadir marcador al mapa", e)
            }
        }

        routeMapView.post { routeMapView.invalidate() }
    }

    fun getDirectDownloadUrl(fileId: String): String {
        return "https://drive.google.com/uc?export=view&id=$fileId"
    }

    private fun showPoiDetailsDialog(comment: String, photoUrl: String?, userPhotoUrl: String?, poiId: Long) {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_poi_details, null)
        val tvComment = view.findViewById<TextView>(R.id.tv_comment)
        val ivPhoto = view.findViewById<ImageView>(R.id.iv_photo)

        tvComment.text = comment ?: "Sin comentario"

        Log.d("PoiDetails", "showPoiDetailsDialog: comment = $comment, photoUrl = $photoUrl, userPhotoUrl = $userPhotoUrl")

        var imageUrlToOpen: String? = null

        // Mostrar la imagen existente si hay una URL
        if (!userPhotoUrl.isNullOrEmpty()) {
            val finalUserPhotoUrl = if (userPhotoUrl.contains("drive.google.com/file/d/")) {
                getDirectDownloadUrl(userPhotoUrl.substringAfter("/d/").substringBefore("/view"))
            } else {
                userPhotoUrl
            }
            Glide.with(this)
                .load(PoiPhotoStorage.glideModel(finalUserPhotoUrl))
                .placeholder(R.drawable.ic_image_link)
                .error(R.drawable.ic_no_image)
                .into(ivPhoto)
            imageUrlToOpen = finalUserPhotoUrl
        } else if (!photoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(PoiPhotoStorage.glideModel(photoUrl))
                .placeholder(R.drawable.ic_image_link)
                .error(R.drawable.ic_no_image)
                .into(ivPhoto)
            imageUrlToOpen = photoUrl
        } else {
            ivPhoto.setImageResource(R.drawable.ic_no_image)
        }

        ivPhoto.setOnClickListener {
            if (!imageUrlToOpen.isNullOrEmpty()) {
                try {
                    PoiPhotoStorage.openPhoto(this, imageUrlToOpen!!)
                } catch (error: Exception) {
                    Toast.makeText(this, "No se puede abrir la foto", Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.setView(view)
            .setTitle("Detalles del Punto de Interés")
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun calculateDistance(points: List<GeoPoint>): Double {
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            totalDistance += calculateHaversineDistance(points[i], points[i + 1])
        }
        return totalDistance
    }

    private fun calculateHaversineDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val R = 6371.0
        val lat1 = Math.toRadians(point1.latitude)
        val lon1 = Math.toRadians(point1.longitude)
        val lat2 = Math.toRadians(point2.latitude)
        val lon2 = Math.toRadians(point2.longitude)

        val dlat = lat2 - lat1
        val dlon = lon2 - lon1

        val a = Math.sin(dlat / 2).pow(2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dlon / 2).pow(2)
        val c = 2 * Math.atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    private fun calculateDuration(routeId: Long): String {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_ESTADISTICAS,
            arrayOf(
                DatabaseHelper.COLUMN_ESTADISTICAS_START_TIME,
                DatabaseHelper.COLUMN_ESTADISTICAS_END_TIME
            ),
            "${DatabaseHelper.COLUMN_ESTADISTICAS_RUTA_ID} = ?",
            arrayOf(routeId.toString()),
            null, null, null
        )

        return try {
            if (cursor.moveToFirst()) {
                val startTimeMillis =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ESTADISTICAS_START_TIME))
                val endTimeMillis =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ESTADISTICAS_END_TIME))
                val durationMillis = endTimeMillis - startTimeMillis
                val durationSeconds = durationMillis / 1000
                val hours = durationSeconds / 3600
                val minutes = (durationSeconds % 3600) / 60
                String.format("%02d:%02d", hours, minutes)
            } else {
                "00:00"
            }
        } catch (e: Exception) {
            Log.e("RouteDetailActivity", "Error calculating duration: ${e.message}")
            "00:00"
        } finally {
            cursor.close()
        }
    }

    private fun formatDistance(distance: Double): String {
        return String.format("%.2f", distance)
    }

    private fun formatDuration(durationMinutes: String): String {
        return durationMinutes
    }
}
