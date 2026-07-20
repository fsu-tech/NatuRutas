package com.example.gpxeditor.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.MapView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.views.overlay.TilesOverlay
import java.util.WeakHashMap

object MapStyleManager {
    private const val PREFERENCES = "map_preferences"
    private const val STYLE_KEY = "map_style"
    private val satelliteLabelOverlays = WeakHashMap<MapView, TilesOverlay>()
    private enum class Style(val value: String, val label: String) {
        STANDARD("standard", "Estándar"),
        TOPOGRAPHIC("topographic", "Topográfico"),
        SATELLITE("satellite", "Satélite")
    }
    private val standardSource: ITileSource by lazy {
        XYTileSource(
            "OpenStreetMap Standard v2", 0, 19, 256, ".png",
            arrayOf("https://tile.openstreetmap.org/"),
            "© OpenStreetMap contributors"
        )
    }
    private val topographicSource: ITileSource by lazy {
        XYTileSource("OpenTopoMap", 0, 17, 256, ".png", arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"), "© OpenStreetMap contributors, SRTM | © OpenTopoMap (CC-BY-SA)")
    }
    private val satelliteSource: ITileSource by lazy {
        object : OnlineTileSourceBase(
            "Esri World Imagery", 0, 19, 256, ".jpg",
            arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
            "Sources: Esri, Vantor, Earthstar Geographics, and the GIS User Community"
        ) {
            override fun getTileURLString(mapTileIndex: Long): String {
                return baseUrl + MapTileIndex.getZoom(mapTileIndex) + "/" +
                    MapTileIndex.getY(mapTileIndex) + "/" + MapTileIndex.getX(mapTileIndex)
            }
        }
    }
    private val satelliteLabelsSource: ITileSource by lazy {
        object : OnlineTileSourceBase(
            "Esri Satellite Labels", 0, 19, 256, ".png",
            arrayOf("https://services.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/"),
            "Sources: Esri and the GIS User Community"
        ) {
            override fun getTileURLString(mapTileIndex: Long): String {
                return baseUrl + MapTileIndex.getZoom(mapTileIndex) + "/" +
                    MapTileIndex.getY(mapTileIndex) + "/" + MapTileIndex.getX(mapTileIndex)
            }
        }
    }
    fun applySavedStyle(context: Context, mapView: MapView) {
        // OSM exige que las aplicaciones se identifiquen y bloquea el User-Agent
        // genérico de las librerías. Se configura antes de solicitar cualquier tesela.
        Configuration.getInstance().userAgentValue =
            "NatuRutas/1.0 (Android; ${context.packageName})"
        val value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(STYLE_KEY, Style.STANDARD.value)
        applyStyle(context, mapView, Style.values().firstOrNull { it.value == value } ?: Style.STANDARD)
    }
    fun showSelector(context: Context, mapView: MapView) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val currentValue = preferences.getString(STYLE_KEY, Style.STANDARD.value)
        val styles = Style.values()
        AlertDialog.Builder(context).setTitle("Tipo de mapa")
            .setSingleChoiceItems(styles.map { it.label }.toTypedArray(), styles.indexOfFirst { it.value == currentValue }.coerceAtLeast(0)) { dialog, which ->
                val selected = styles[which]
                preferences.edit().putString(STYLE_KEY, selected.value).apply()
                applyStyle(context, mapView, selected)
                dialog.dismiss()
            }.setNegativeButton("Cancelar", null).show()
    }
    private fun applyStyle(context: Context, mapView: MapView, style: Style) {
        satelliteLabelOverlays.remove(mapView)?.let { labelsOverlay ->
            mapView.overlays.remove(labelsOverlay)
            labelsOverlay.onDetach(mapView)
        }
        mapView.setTileSource(when (style) {
            Style.STANDARD -> standardSource
            Style.TOPOGRAPHIC -> topographicSource
            Style.SATELLITE -> satelliteSource
        })
        if (style == Style.SATELLITE) {
            val labelsProvider = MapTileProviderBasic(context, satelliteLabelsSource)
            val labelsOverlay = TilesOverlay(labelsProvider, context)
            satelliteLabelOverlays[mapView] = labelsOverlay
            // La referencia debe quedar debajo de rutas, POI y atribución.
            mapView.overlays.add(0, labelsOverlay)
        }
        mapView.invalidate()
    }
}
