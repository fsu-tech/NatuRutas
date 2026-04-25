package com.example.gpxeditor.view.fragments

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gpxeditor.R
import com.example.gpxeditor.view.adapters.RoutesAdapter
import com.example.gpxeditor.model.database.DatabaseHelper
import com.example.gpxeditor.model.entities.Route
import java.io.OutputStreamWriter
import java.io.IOException

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
                    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                    writer.write("<gpx version=\"1.1\" creator=\"TuAppAndroid\">\n")

                    for (route in routes) {
                        writer.write(" <trk>\n  <trkseg>\n")

                        val coordenadas = dbHelper.getRouteCoordinates(route.id)
                        for (coordenada in coordenadas) {
                            writer.write("   <trkpt lat=\"${coordenada.latitud}\" lon=\"${coordenada.longitud}\" ele=\"${coordenada.altura}\">\n")
                            writer.write("   </trkpt>\n")
                        }

                        writer.write("  </trkseg>\n </trk>\n")

                        val puntosInteres = dbHelper.getPuntosInteresByRouteId(route.id)
                        for (punto in puntosInteres) {
                            writer.write("  <wpt lat=\"${punto.latitud}\" lon=\"${punto.longitud}\">\n")
                            writer.write("   <name>${punto.comentario}</name>\n")
                            writer.write("   <desc>${punto.comentario}</desc>\n")
                            punto.imagenUrl?.let { writer.write("   <link href=\"$it\"/>\n") }
                            writer.write("  </wpt>\n")
                        }
                    }

                    writer.write("</gpx>\n")
                }
                Toast.makeText(requireContext(), "Archivo GPX exportado con éxito", Toast.LENGTH_SHORT).show()
                Log.d("SavedRoutesFragment", "GPX file created successfully")
            }
        } catch (e: IOException) {
            Log.e("SavedRoutesFragment", "Error creating GPX file: ${e.message}")
            Toast.makeText(requireContext(), "Ocurrió un error al exportar", Toast.LENGTH_SHORT).show()
        }
    }
}

