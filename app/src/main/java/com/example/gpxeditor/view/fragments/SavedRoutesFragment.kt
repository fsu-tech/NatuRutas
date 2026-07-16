package com.example.gpxeditor.view.fragments

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gpxeditor.R
import com.example.gpxeditor.view.adapters.RoutesAdapter
import com.example.gpxeditor.model.database.DatabaseHelper
import com.example.gpxeditor.model.entities.Route
import java.io.OutputStreamWriter
import java.io.IOException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SavedRoutesFragment : Fragment(R.layout.fragment_saved_routes),
    RoutesAdapter.OnItemClickListener {
    private val wikilocPackage = "com.wikiloc.wikilocandroid"
    private lateinit var routesList: RecyclerView
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var routesAdapter: RoutesAdapter
    private val selectedRoutes = mutableListOf<Route>()
    private var tempRoutes: List<Route> = emptyList() // Guarda temporalmente las rutas a exportar
    private val CREATE_FILE_REQUEST_CODE = 40
    private var tiempoEntrada: Long = 0
    private var pendingWikilocRoute: Route? = null

    private val writeStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val route = pendingWikilocRoute
        pendingWikilocRoute = null
        if (granted && route != null) {
            prepareWikilocExport(route)
        } else if (!granted) {
            Toast.makeText(
                requireContext(),
                "Se necesita permiso para exportar las fotos",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        tiempoEntrada = System.currentTimeMillis()
        // Recargar la lista de rutas al volver al fragmento
        if (::dbHelper.isInitialized && ::routesAdapter.isInitialized) {
            val routes = dbHelper.getAllRoutes()
            routesAdapter = RoutesAdapter(routes, this)
            routesList.adapter = routesAdapter
        }
    }

    override fun onPause() {
        super.onPause()
        val segundos = (System.currentTimeMillis() - tiempoEntrada) / 1000
        dbHelper.registrarEvento("MisRutas", "tiempo_en_pantalla", "${segundos}s")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = DatabaseHelper(requireContext())

        // Configurar RecyclerView
        routesList = view.findViewById(R.id.routesList)
        routesList.layoutManager = LinearLayoutManager(requireContext())

        val routes = dbHelper.getAllRoutes()
        routesAdapter = RoutesAdapter(routes, this)
        routesList.adapter = routesAdapter

    }

    override fun onItemClick(route: Route) {
        if (selectedRoutes.contains(route)) {
            selectedRoutes.remove(route)
        } else {
            selectedRoutes.add(route)
        }
        dbHelper.registrarEvento("MisRutas", "click_ruta", route.name)
        Log.d("SavedRoutesFragment", "Route selected: ${route.name}")
    }

    fun exportRoutesToGpx(routes: List<Route>) {
        dbHelper.registrarEvento("MisRutas", "exportar_gpx", "${routes.size} rutas")
        tempRoutes = routes // Guarda las rutas que deben exportarse
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_TITLE, "rutas.gpx")
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
    }

    fun shareRoutesAsGpx(routes: List<Route>) {
        if (routes.isEmpty()) return

        try {
            dbHelper.registrarEvento("MisRutas", "compartir_gpx", "${routes.size} rutas")
            val shareDirectory = File(requireContext().cacheDir, "shared_routes").apply { mkdirs() }
            val routeName = routes.singleOrNull()?.name
                ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                ?.take(60)
                ?.ifBlank { "ruta" }
                ?: "rutas"
            val gpxFile = File(shareDirectory, "$routeName.gpx")
            gpxFile.writeText(buildGpxContent(routes), Charsets.UTF_8)

            val contentUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                gpxFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                clipData = ClipData.newRawUri("Ruta GPX", contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir ruta"))
        } catch (e: IOException) {
            Log.e("SavedRoutesFragment", "Error sharing GPX file", e)
            Toast.makeText(requireContext(), "No se pudo preparar la ruta para compartir", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportRouteToWikiloc(route: Route) {
        if (
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingWikilocRoute = route
            writeStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        prepareWikilocExport(route)
    }

    private fun prepareWikilocExport(route: Route) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val safeRouteName = safeFilePart(route.name).ifBlank { "ruta" }
                    val albumName = "NatuRutas/$safeRouteName"
                    val photoCount = exportRoutePhotos(route, albumName)
                    val shareDirectory = File(requireContext().cacheDir, "shared_routes").apply { mkdirs() }
                    val gpxFile = File(shareDirectory, "${safeRouteName}_wikiloc.gpx")
                    gpxFile.writeText(
                        buildGpxContent(listOf(route), numberWaypoints = true),
                        Charsets.UTF_8
                    )
                    WikilocExportResult(gpxFile, albumName, photoCount)
                }
                dbHelper.registrarEvento("MisRutas", "exportar_wikiloc", route.name)
                showWikilocExportReady(result)
            } catch (error: Exception) {
                Log.e("SavedRoutesFragment", "Error preparing Wikiloc export", error)
                Toast.makeText(
                    requireContext(),
                    "No se pudo preparar la exportación a Wikiloc",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun exportRoutePhotos(route: Route, albumName: String): Int {
        var exportedCount = 0
        dbHelper.getPuntosInteresByRouteId(route.id).forEachIndexed { index, point ->
            val source = listOfNotNull(point.userImagenUrl, point.imagenUrl)
                .map(::File)
                .firstOrNull { it.isFile }
                ?: return@forEachIndexed
            val label = safeFilePart(point.comentario).ifBlank { "punto_interes" }
            val displayName = "%02d_%s.jpg".format(index + 1, label.take(45))
            copyPhotoToPictures(
                source,
                albumName,
                displayName,
                point.latitud,
                point.longitud
            )
            exportedCount++
        }
        return exportedCount
    }

    private fun copyPhotoToPictures(
        source: File,
        albumName: String,
        displayName: String,
        latitude: Double,
        longitude: Double
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = requireContext().contentResolver
            val relativePath = "${Environment.DIRECTORY_PICTURES}/$albumName/"
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val existingUri = resolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(displayName, relativePath),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContentUris.withAppendedId(collection, cursor.getLong(0))
                } else {
                    null
                }
            }
            val destinationUri = existingUri ?: resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            ) ?: throw IOException("No se pudo crear $displayName")

            resolver.openOutputStream(destinationUri, "wt").use { output ->
                requireNotNull(output) { "No se pudo escribir $displayName" }
                source.inputStream().use { input -> input.copyTo(output) }
            }
            if (existingUri == null) {
                resolver.update(
                    destinationUri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            try {
                resolver.openFileDescriptor(destinationUri, "rw")?.use { descriptor ->
                    ExifInterface(descriptor.fileDescriptor).apply {
                        setPhotoLocation(latitude, longitude)
                        saveAttributes()
                    }
                }
            } catch (error: IOException) {
                Log.w("SavedRoutesFragment", "No se pudo escribir la ubicación EXIF", error)
            }
        } else {
            @Suppress("DEPRECATION")
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val destination = File(pictures, "$albumName/$displayName")
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            try {
                ExifInterface(destination.absolutePath).apply {
                    setPhotoLocation(latitude, longitude)
                    saveAttributes()
                }
            } catch (error: IOException) {
                Log.w("SavedRoutesFragment", "No se pudo escribir la ubicación EXIF", error)
            }
            MediaScannerConnection.scanFile(
                requireContext(),
                arrayOf(destination.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
        }
    }

    private fun showWikilocExportReady(result: WikilocExportResult) {
        val photoMessage = if (result.photoCount > 0) {
            "Se han preparado ${result.photoCount} fotos en Imágenes/${result.albumName}. " +
                "En Wikiloc, pulsa Añade fotos, selecciónalas en Fotos tomadas durante la " +
                "actividad y toca Hecho."
        } else {
            "La ruta no contiene fotos locales. Se compartirá el GPX con sus waypoints."
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Ruta preparada para Wikiloc")
            .setMessage(photoMessage)
            .setPositiveButton("Abrir en Wikiloc") { _, _ -> openGpxInWikiloc(result.gpxFile) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openGpxInWikiloc(gpxFile: File) {
        val contentUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            gpxFile
        )
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/gpx+xml")
            clipData = ClipData.newRawUri("Ruta para Wikiloc", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val wikilocIntent = Intent(openIntent).setPackage(wikilocPackage)
        if (wikilocIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(wikilocIntent)
        } else {
            startActivity(Intent.createChooser(openIntent, "Abrir ruta GPX"))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                writeFile(uri, tempRoutes) // Usa las rutas correctas
            } ?: run {
                Toast.makeText(requireContext(), "No se seleccionó un archivo", Toast.LENGTH_SHORT).show()
                Log.w("SavedRoutesFragment", "No file selected for export")
            }
        }
    }

    private fun writeFile(uri: Uri, routes: List<Route>) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(buildGpxContent(routes))
                }
                Toast.makeText(requireContext(), "Archivo GPX exportado con éxito", Toast.LENGTH_SHORT).show()
                Log.d("SavedRoutesFragment", "GPX file created successfully")
            }
        } catch (e: IOException) {
            Log.e("SavedRoutesFragment", "Error creating GPX file: ${e.message}")
            Toast.makeText(requireContext(), "Ocurrió un error al exportar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildGpxContent(
        routes: List<Route>,
        numberWaypoints: Boolean = false
    ): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"NatuRutas\" xmlns=\"http://www.topografix.com/GPX/1/1\">")

        routes.forEach { route ->
            dbHelper.getPuntosInteresByRouteId(route.id).forEachIndexed { index, point ->
                val pointName = if (numberWaypoints) {
                    "%02d - %s".format(
                        index + 1,
                        point.comentario.ifBlank { "Punto de interés" }
                    )
                } else {
                    point.comentario
                }
                appendLine(" <wpt lat=\"${point.latitud}\" lon=\"${point.longitud}\">")
                appendLine("  <name>${escapeXml(pointName)}</name>")
                appendLine("  <desc>${escapeXml(point.comentario)}</desc>")
                point.imagenUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { appendLine("  <link href=\"${escapeXml(it)}\"/>") }
                appendLine(" </wpt>")
            }
        }

        routes.forEach { route ->
            appendLine(" <trk>")
            appendLine("  <name>${escapeXml(route.name)}</name>")
            appendLine("  <trkseg>")
            val coordinates = dbHelper.getRouteCoordinates(route.id)
            val timeRange = dbHelper.getRouteTimeRange(route.id)
            coordinates.forEachIndexed { index, coordinate ->
                appendLine("   <trkpt lat=\"${coordinate.latitud}\" lon=\"${coordinate.longitud}\">")
                appendLine("    <ele>${coordinate.altura}</ele>")
                trackPointTime(timeRange, index, coordinates.size)?.let { time ->
                    appendLine("    <time>${formatGpxTime(time)}</time>")
                }
                appendLine("   </trkpt>")
            }
            appendLine("  </trkseg>")
            appendLine(" </trk>")
        }
        appendLine("</gpx>")
    }

    private fun trackPointTime(
        timeRange: Pair<Long, Long>?,
        index: Int,
        pointCount: Int
    ): Long? {
        val (startTime, endTime) = timeRange ?: return null
        if (startTime <= 0L || endTime < startTime) return null
        if (pointCount <= 1) return startTime
        return startTime + ((endTime - startTime) * index / (pointCount - 1))
    }

    private fun formatGpxTime(timeMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timeMillis))

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun safeFilePart(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_', '.')
        .take(60)

    private fun ExifInterface.setPhotoLocation(latitude: Double, longitude: Double) {
        setAttribute(
            ExifInterface.TAG_GPS_LATITUDE,
            coordinateToExif(latitude)
        )
        setAttribute(
            ExifInterface.TAG_GPS_LATITUDE_REF,
            if (latitude >= 0) "N" else "S"
        )
        setAttribute(
            ExifInterface.TAG_GPS_LONGITUDE,
            coordinateToExif(longitude)
        )
        setAttribute(
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            if (longitude >= 0) "E" else "W"
        )
    }

    private fun coordinateToExif(coordinate: Double): String {
        val absolute = abs(coordinate)
        val degrees = absolute.toInt()
        val minuteValue = (absolute - degrees) * 60
        val minutes = minuteValue.toInt()
        val seconds = ((minuteValue - minutes) * 60 * 10_000).toLong()
        return "$degrees/1,$minutes/1,$seconds/10000"
    }

    private data class WikilocExportResult(
        val gpxFile: File,
        val albumName: String,
        val photoCount: Int
    )
}
