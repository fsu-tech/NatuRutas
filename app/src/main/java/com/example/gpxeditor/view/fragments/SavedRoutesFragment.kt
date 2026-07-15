package com.example.gpxeditor.view.fragments

import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gpxeditor.R
import com.example.gpxeditor.view.adapters.RoutesAdapter
import com.example.gpxeditor.model.database.DatabaseHelper
import com.example.gpxeditor.model.entities.Route
import java.io.OutputStreamWriter
import java.io.IOException
import java.io.File

class SavedRoutesFragment : Fragment(R.layout.fragment_saved_routes),
    RoutesAdapter.OnItemClickListener {
    private lateinit var routesList: RecyclerView
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var routesAdapter: RoutesAdapter
    private val selectedRoutes = mutableListOf<Route>()
    private var tempRoutes: List<Route> = emptyList() // Guarda temporalmente las rutas a exportar
    private val CREATE_FILE_REQUEST_CODE = 40
    private var tiempoEntrada: Long = 0

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

    private fun buildGpxContent(routes: List<Route>): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<gpx version=\"1.1\" creator=\"NatuRutas\" xmlns=\"http://www.topografix.com/GPX/1/1\">")

        routes.forEach { route ->
            dbHelper.getPuntosInteresByRouteId(route.id).forEach { point ->
                appendLine(" <wpt lat=\"${point.latitud}\" lon=\"${point.longitud}\">")
                appendLine("  <name>${escapeXml(point.comentario)}</name>")
                appendLine("  <desc>${escapeXml(point.comentario)}</desc>")
                point.imagenUrl?.let { appendLine("  <link href=\"${escapeXml(it)}\"/>") }
                appendLine(" </wpt>")
            }
        }

        routes.forEach { route ->
            appendLine(" <trk>")
            appendLine("  <name>${escapeXml(route.name)}</name>")
            appendLine("  <trkseg>")
            dbHelper.getRouteCoordinates(route.id).forEach { coordinate ->
                appendLine("   <trkpt lat=\"${coordinate.latitud}\" lon=\"${coordinate.longitud}\">")
                appendLine("    <ele>${coordinate.altura}</ele>")
                appendLine("   </trkpt>")
            }
            appendLine("  </trkseg>")
            appendLine(" </trk>")
        }
        appendLine("</gpx>")
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
