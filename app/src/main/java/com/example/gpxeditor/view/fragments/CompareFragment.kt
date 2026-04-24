package com.example.gpxeditor.view.fragments

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.example.gpxeditor.view.customviews.DesnivelPositivoAcumuladoChartView
import com.example.gpxeditor.view.customviews.DistanceChartView
import com.example.gpxeditor.view.customviews.DurationChartView
import com.example.gpxeditor.view.customviews.PerfilElevacionView
import com.example.gpxeditor.R
import com.example.gpxeditor.view.customviews.VelocidadPromedioView
import com.example.gpxeditor.model.database.DatabaseHelper
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class CompareFragment : Fragment() {

    private lateinit var route1Spinner: Spinner
    private lateinit var route2Spinner: Spinner
    private lateinit var compareButton: Button
    private lateinit var metricsButton: Button
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var graphicLayout: LinearLayout
    private val selectedMetrics = mutableSetOf<String>()

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()
    private var tiempoEntrada: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = requireContext().getSharedPreferences("ChartData", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_compare, container, false)

        route1Spinner = view.findViewById(R.id.route1Spinner)
        route2Spinner = view.findViewById(R.id.route2Spinner)
        compareButton = view.findViewById(R.id.compareButton)
        metricsButton = view.findViewById(R.id.metricsButton)
        graphicLayout = view.findViewById(R.id.graphicLayout)

        dbHelper = DatabaseHelper(requireContext())

        val routes = dbHelper.getAllRoutes() // Obtener la lista de objetos Route

        if (routes.isEmpty()) { // Verificar si la lista está vacía
            val emptyAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("No hay rutas disponibles"))
            emptyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            route1Spinner.adapter = emptyAdapter
            route2Spinner.adapter = emptyAdapter
        } else {
            val routeNames = routes.map { it.name } // Obtener los nombres de las rutas
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, routeNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            route1Spinner.adapter = adapter
            route2Spinner.adapter = adapter
        }

        compareButton.setOnClickListener {
            val ruta1 = route1Spinner.selectedItem?.toString() ?: ""
            val ruta2 = route2Spinner.selectedItem?.toString() ?: ""
            dbHelper.registrarEvento("Comparar", "comparar_rutas", "$ruta1 vs $ruta2")
            compareRoutes()
        }

        metricsButton.setOnClickListener {
            showMetricsMenu(it)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        tiempoEntrada = System.currentTimeMillis()
        restoreChartData()
    }

    override fun onPause() {
        super.onPause()
        val segundos = (System.currentTimeMillis() - tiempoEntrada) / 1000
        dbHelper.registrarEvento("Comparar", "tiempo_en_pantalla", "${segundos}s")
        saveChartData()
    }

    private fun showMetricsMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.metrics_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.metric_distance -> setSingleMetric("distance")
                R.id.metric_duration -> setSingleMetric("duration")
                R.id.metric_desnivel -> setSingleMetric("desnivel")
                R.id.metric_velocidad_promedio -> setSingleMetric("velocidad_promedio")
                R.id.metric_perfil -> setSingleMetric("perfil") // Añadido perfil
            }
            true
        }
        popup.show()
    }

    private fun setSingleMetric(metric: String) {
        selectedMetrics.clear()
        selectedMetrics.add(metric)
        dbHelper.registrarEvento("Comparar", "seleccionar_metrica", metric)
    }

    private fun compareRoutes() {
        Log.d("CompareFragment", "Inicio de compareRoutes")

        val route1Name = route1Spinner.selectedItem?.toString()
        val route2Name = route2Spinner.selectedItem?.toString()

        Log.d("CompareFragment", "Ruta 1 seleccionada: $route1Name, Ruta 2 seleccionada: $route2Name")

        if (route1Name == "No hay rutas disponibles" || route2Name == "No hay rutas disponibles") {
            Log.e("CompareFragment", "No hay rutas para comparar")
            graphicLayout.removeAllViews()
            return
        }

        val route1 = dbHelper.getAllRoutes().find { it.name == route1Name }
        val route2 = dbHelper.getAllRoutes().find { it.name == route2Name }

        if (route1 == null || route2 == null) {
            Log.e("CompareFragment", "Ruta no encontrada: $route1Name o $route2Name")
            return
        }

        Log.d("CompareFragment", "Rutas encontradas: ${route1.id} y ${route2.id}")

        val estadisticas1 = dbHelper.getEstadisticasByRouteId(route1.id)
        val estadisticas2 = dbHelper.getEstadisticasByRouteId(route2.id)

        Log.d("CompareFragment", "Estadisticas 1: $estadisticas1, Estadisticas 2: $estadisticas2")

        graphicLayout.removeAllViews()

        if (estadisticas1 != null && estadisticas2 != null) {
            if (selectedMetrics.contains("distance")) {
                Log.d("CompareFragment", "Mostrando distancia")
                val distanceChart = DistanceChartView(requireContext(), estadisticas1.distancia, estadisticas2.distancia)
                graphicLayout.addView(distanceChart)
            }

            if (selectedMetrics.contains("duration")) {
                Log.d("CompareFragment", "Mostrando duración")
                Log.d("CompareFragment", "TiempoTotal 1: ${estadisticas1.tiempoTotal}, TiempoTotal 2: ${estadisticas2.tiempoTotal}")
                val formattedDuration1 = formatDuration(estadisticas1.tiempoTotal)
                val formattedDuration2 = formatDuration(estadisticas2.tiempoTotal)
                Log.d("CompareFragment", "Duración formateada 1: $formattedDuration1, Duración formateada 2: $formattedDuration2")
                val durationChart = DurationChartView(requireContext(), formattedDuration1, formattedDuration2)
                graphicLayout.addView(durationChart)
            }

            if (selectedMetrics.contains("desnivel")) {
                Log.d("CompareFragment", "Mostrando desnivel")
                val desnivelChart = DesnivelPositivoAcumuladoChartView(requireContext(), estadisticas1.desnivelAcumulado, estadisticas2.desnivelAcumulado)
                graphicLayout.addView(desnivelChart)
            }

            if (selectedMetrics.contains("velocidad_promedio")) {
                Log.d("CompareFragment", "Mostrando velocidad promedio")
                Log.d("CompareFragment", "Velocidad promedio 1: ${estadisticas1.velocidad_promedio}, Velocidad promedio 2: ${estadisticas2.velocidad_promedio}")
                val speedChart = VelocidadPromedioView(requireContext(), estadisticas1.velocidad_promedio, estadisticas2.velocidad_promedio)
                graphicLayout.addView(speedChart)
            }

            if (selectedMetrics.contains("perfil")) {
                Log.d("CompareFragment", "Mostrando perfil")
                val perfil1 = PerfilElevacionView(requireContext(), obtenerAltitudes(route1.id), "Ruta 1", Color.BLUE).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                }
                val perfil2 = PerfilElevacionView(requireContext(), obtenerAltitudes(route2.id), "Ruta 2", Color.RED).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                }
                graphicLayout.addView(perfil1)
                graphicLayout.addView(perfil2)
            }
        } else {
            Log.e("CompareFragment", "Estadísticas no encontradas para: $route1Name o $route2Name")
        }

        Log.d("CompareFragment", "Fin de compareRoutes")
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60)) % 24
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun saveChartData() {
        val chartData = ChartData(selectedMetrics.toList())
        val json = gson.toJson(chartData)
        sharedPreferences.edit().putString("chartData", json).apply()
    }

    private fun restoreChartData() {
        val json = sharedPreferences.getString("chartData", null)
        if (json != null) {
            try {
                val chartData = gson.fromJson(json, ChartData::class.java)
                selectedMetrics.clear()
                selectedMetrics.addAll(chartData.selectedMetrics)
                if (selectedMetrics.isNotEmpty()) {
                    compareRoutes()
                }
            } catch (e: JsonSyntaxException) {
                Log.e("CompareFragment", "Error al parsear datos guardados: ${e.message}")
            }
        }
    }

    data class ChartData(val selectedMetrics: List<String>)

    private fun obtenerAltitudes(routeId: Long): List<Double> {
        val db =dbHelper.readableDatabase
        val altitudes = mutableListOf<Double>()

        val cursor = db.query(
            DatabaseHelper.TABLE_PUNTOS_RUTA,
            arrayOf(DatabaseHelper.COLUMN_PUNTOS_RUTA_ALTURA),
            "${DatabaseHelper.COLUMN_PUNTOS_RUTA_RUTA_ID} = ?",
            arrayOf(routeId.toString()),
            null,
            null,
            DatabaseHelper.COLUMN_PUNTOS_RUTA_ORDEN
        )

        try {
            while (cursor.moveToNext()) {
                val altitude = cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PUNTOS_RUTA_ALTURA))
                altitudes.add(altitude)
            }
        } catch (e: Exception) {
            Log.e("CompareFragment", "Error getting altitudes: ${e.message}")
        } finally {
            cursor.close()
        }

        return altitudes
    }
}