package com.example.gpxeditor.model.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.util.Log
import com.example.gpxeditor.model.entities.Coordenada
import com.example.gpxeditor.model.entities.PuntoInteres
import com.example.gpxeditor.model.entities.Route
import org.osmdroid.util.GeoPoint
import java.lang.IllegalArgumentException

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "rutas.db"
        private const val DATABASE_VERSION = 8

        // Preferencias de telemetría
        const val PREFS_TELEMETRIA = "telemetria_prefs"
        const val KEY_USUARIO = "usuario_nombre"
        const val KEY_SESION = "sesion_id"
        const val KEY_USUARIO_ID = "usuarioId" // Clave para el ID único del usuario

        // EventosUsuario Table
        const val TABLE_EVENTOS = "EventosUsuario"
        const val COLUMN_EVENTO_ID = "id"
        const val COLUMN_EVENTO_TIMESTAMP = "timestamp"
        const val COLUMN_EVENTO_PANTALLA = "pantalla"
        const val COLUMN_EVENTO_TIPO = "evento"
        const val COLUMN_EVENTO_DETALLE = "detalle"
        const val COLUMN_EVENTO_USUARIO = "usuario"
        const val COLUMN_EVENTO_SESION = "sesion_id"

        // Rutas Table (sin start_time y end_time, con tipo_ruta)
         const val TABLE_RUTAS = "Rutas"
         const val COLUMN_RUTA_ID = "id"
         const val COLUMN_RUTA_NOMBRE = "nombre"
         const val COLUMN_RUTA_FECHA = "fecha"
         const val COLUMN_RUTA_TIPO = "tipo_ruta"

        // PuntosRuta Table
         const val TABLE_PUNTOS_RUTA = "PuntosRuta"
         const val COLUMN_PUNTOS_RUTA_ID = "id"
         const val COLUMN_PUNTOS_RUTA_RUTA_ID = "ruta_id"
         const val COLUMN_PUNTOS_RUTA_LATITUD = "latitud"
         const val COLUMN_PUNTOS_RUTA_LONGITUD = "longitud"
         const val COLUMN_PUNTOS_RUTA_ORDEN = "orden"
         const val COLUMN_PUNTOS_RUTA_ALTURA = "altura"


        // Estadisticas Table
         const val TABLE_ESTADISTICAS = "Estadisticas"
         const val COLUMN_ESTADISTICAS_ID = "id"
         const val COLUMN_ESTADISTICAS_RUTA_ID = "ruta_id"
         const val COLUMN_ESTADISTICAS_DISTANCIA = "distancia"
         const val COLUMN_ESTADISTICAS_START_TIME = "start_time"
         const val COLUMN_ESTADISTICAS_END_TIME = "end_time"
         const val COLUMN_ESTADISTICAS_TIEMPO_TOTAL = "tiempo_total"
         const val COLUMN_ESTADISTICAS_VELOCIDAD_PROMEDIO = "velocidad_promedio"
         const val COLUMN_ESTADISTICAS_DESNIVEL_ACUMULADO = "desnivel_acumulado"

        // PuntosInteres Table
         const val TABLE_PUNTOS_INTERES = "PuntosInteres"
         const val COLUMN_PUNTOS_INTERES_ID = "id"
         const val COLUMN_PUNTOS_INTERES_RUTA_ID = "ruta_id"
         const val COLUMN_PUNTOS_INTERES_LATITUD = "latitud"
         const val COLUMN_PUNTOS_INTERES_LONGITUD = "longitud"
         const val COLUMN_PUNTOS_INTERES_COMENTARIO = "comentario"
         const val COLUMN_PUNTOS_INTERES_IMAGEN_URL = "imagen_url"
        const val COLUMN_PUNTOS_INTERES_USER_IMAGEN_URL = "user_imagen_url"

        // Insignias Table
        const val TABLE_INSIGNIAS = "Insignias"
        const val COLUMN_INSIGNIAS_ID = "id"
        const val COLUMN_INSIGNIAS_NOMBRE = "nombre"
        const val COLUMN_INSIGNIAS_FECHA_OBTENCION = "fecha_obtencion"
        const val COLUMN_INSIGNIAS_RUTA_ID = "ruta_id"
        const val COLUMN_INSIGNIAS_TIPO = "tipo_insignia"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Crear tablas
        db.execSQL("""
            CREATE TABLE Rutas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                fecha TEXT NOT NULL,
                tipo_ruta TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE PuntosRuta (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ruta_id INTEGER NOT NULL,
                latitud REAL NOT NULL,
                longitud REAL NOT NULL,
                orden INTEGER NOT NULL,
                altura REAL NOT NULL,
                tiempo INTEGER,
                FOREIGN KEY (ruta_id) REFERENCES Rutas(id) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE Estadisticas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ruta_id INTEGER NOT NULL,
                distancia REAL NOT NULL,
                start_time INTEGER,
                end_time INTEGER,
                tiempo_total INTEGER NOT NULL,
                velocidad_promedio REAL NOT NULL,
                desnivel_acumulado REAL NOT NULL,
                FOREIGN KEY (ruta_id) REFERENCES Rutas(id) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE PuntosInteres (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ruta_id INTEGER NOT NULL,
                latitud REAL NOT NULL,
                longitud REAL NOT NULL,
                comentario TEXT NOT NULL,
                imagen_url TEXT,
                user_imagen_url TEXT, 
                FOREIGN KEY (ruta_id) REFERENCES Rutas(id) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE Insignias (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                fecha_obtencion TEXT NOT NULL,
                ruta_id INTEGER,
                tipo_insignia TEXT NOT NULL,
                FOREIGN KEY (ruta_id) REFERENCES Rutas(id) ON DELETE CASCADE
            )
        """)

        db.execSQL("""
            CREATE TABLE EventosUsuario (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                pantalla TEXT NOT NULL,
                evento TEXT NOT NULL,
                detalle TEXT,
                usuario TEXT,
                sesion_id TEXT
            )
        """)

        // Índices para mejorar las consultas
        db.execSQL("CREATE INDEX idx_ruta_id ON PuntosRuta(ruta_id)")
        db.execSQL("CREATE INDEX idx_estadisticas_ruta_id ON Estadisticas(ruta_id)")
        db.execSQL("CREATE INDEX idx_puntos_interes_ruta_id ON PuntosInteres(ruta_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 7) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS EventosUsuario (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp INTEGER NOT NULL,
                    pantalla TEXT NOT NULL,
                    evento TEXT NOT NULL,
                    detalle TEXT,
                    usuario TEXT,
                    sesion_id TEXT
                )
            """)
        }
        if (oldVersion == 7) {
            // Solo para upgrades desde v7: la tabla existía pero sin estas columnas
            db.execSQL("ALTER TABLE EventosUsuario ADD COLUMN usuario TEXT")
            db.execSQL("ALTER TABLE EventosUsuario ADD COLUMN sesion_id TEXT")
        }
    }

    fun registrarEvento(pantalla: String, evento: String, detalle: String? = null, rutaId: Long? = null) {
        val prefs = context.getSharedPreferences(PREFS_TELEMETRIA, android.content.Context.MODE_PRIVATE)
        val usuario = prefs.getString(KEY_USUARIO, "desconocido")
        val sesionId = prefs.getString(KEY_SESION, "sin_sesion")
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_EVENTO_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_EVENTO_PANTALLA, pantalla)
            put(COLUMN_EVENTO_TIPO, evento)
            put(COLUMN_EVENTO_DETALLE, detalle)
            put(COLUMN_EVENTO_USUARIO, usuario)
            put(COLUMN_EVENTO_SESION, sesionId)
        }
        val rowId = db.insert(TABLE_EVENTOS, null, values)
        android.util.Log.d("TELEMETRIA", "Evento guardado [id=$rowId] usuario=$usuario | pantalla=$pantalla | evento=$evento | detalle=$detalle")
    }

    data class PuntoInteresData(
        val id: Int,
        val latitud: Double,
        val longitud: String,
        val comentario: String,
        val imagenPath: String?,
        val userImagenPath: String?
    )

    fun insertRoute(
        nombre: String,
        fecha: String,
        puntos: List<Triple<Double, Double, Double>>,
        startTime: Long,
        endTime: Long,
        puntosInteres: List<PuntoInteresData> = emptyList(),
        tipoRuta: String? = null // Nuevo parámetro
    ): Long {
        val db = writableDatabase
        var rutaId = -1L

        db.beginTransaction()
        Log.d("DatabaseHelper", "insertRoute: Transaccion iniciada")
        try {
            // Inserción en Rutas
            val rutaValues = ContentValues().apply {
                put("nombre", nombre)
                put("fecha", fecha)
                if (tipoRuta != null) {
                    put("tipo_ruta", tipoRuta)
                }
            }
            rutaId = db.insert("Rutas", null, rutaValues)
            Log.d("DatabaseHelper", "insertRoute: Ruta insertada, rutaId = $rutaId")

            // Inserción en PuntosRuta y Estadisticas
            if (rutaId != -1L && puntos.isNotEmpty()) {
                val statistics = calculateStatistics(puntos, startTime, endTime)
                puntos.distinct().forEachIndexed { index, (lat, lon, alt) ->
                    val puntoValues = ContentValues().apply {
                        put("ruta_id", rutaId)
                        put("latitud", lat)
                        put("longitud", lon)
                        put("altura", alt)
                        put("orden", index)
                        put("tiempo", System.currentTimeMillis() - startTime)
                    }
                    db.insert("PuntosRuta", null, puntoValues)
                    Log.d("DatabaseHelper", "insertRoute: Punto de ruta insertado, orden = $index")
                }
                val estadisticasValues = ContentValues().apply {
                    put("ruta_id", rutaId)
                    put("distancia", statistics.distancia)
                    put("start_time", startTime)
                    put("end_time", endTime)
                    put("tiempo_total", statistics.tiempoTotal)
                    put("velocidad_promedio", statistics.velocidad_promedio)
                    put("desnivel_acumulado", statistics.desnivelAcumulado)
                }
                db.insert("Estadisticas", null, estadisticasValues)
                Log.d("DatabaseHelper", "insertRoute: Estadisticas insertadas")
            }

            // Inserción en PuntosInteres
            puntosInteres.forEach { punto ->
                Log.d("DatabaseHelper", "insertRoute: userImagenPath: ${punto.userImagenPath}")
                val puntoInteresValues = ContentValues().apply {
                    put("ruta_id", rutaId)
                    put("latitud", punto.latitud)
                    put("longitud", punto.longitud)
                    put("comentario", punto.comentario)
                    put("imagen_url", punto.imagenPath)
                    put("user_imagen_url", punto.userImagenPath)
                }
                db.insert("PuntosInteres", null, puntoInteresValues)
                Log.d("DatabaseHelper", "insertRoute: Punto de interés insertado")
            }

            db.setTransactionSuccessful()
            Log.d("DatabaseHelper", "insertRoute: Transaccion exitosa")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "insertRoute: Error inserting route: ${e.message}", e)
        } finally {
            db.endTransaction()
            Log.d("DatabaseHelper", "insertRoute: Transaccion finalizada")
        }
        Log.d("DatabaseHelper", "insertRoute: RutaId final: $rutaId")

        return rutaId
    }

    fun getRouteCoordinates(routeId: Long): List<Coordenada> {
        val db = readableDatabase
        val coordinates = mutableListOf<Coordenada>()
        val cursor = db.query(
            TABLE_PUNTOS_RUTA,
            arrayOf(COLUMN_PUNTOS_RUTA_LATITUD, COLUMN_PUNTOS_RUTA_LONGITUD, COLUMN_PUNTOS_RUTA_ALTURA),
            "$COLUMN_PUNTOS_RUTA_RUTA_ID = ?",
            arrayOf(routeId.toString()),
            null,
            null,
            COLUMN_PUNTOS_RUTA_ORDEN
        )

        try {
            while (cursor.moveToNext()) {
                val latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(
                    COLUMN_PUNTOS_RUTA_LATITUD
                ))
                val longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(
                    COLUMN_PUNTOS_RUTA_LONGITUD
                ))
                val altura = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_PUNTOS_RUTA_ALTURA))
                coordinates.add(Coordenada(latitude, longitude, altura))
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting route coordinates: ${e.message}")
        } finally {
            cursor.close()
        }
        return coordinates
    }

    private fun calculateStatistics(
        puntos: List<Triple<Double, Double, Double>>,
        startTime: Long,
        endTime: Long
    ): EstadisticasData {
        var totalDistance = 0.0
        var elevationGain = 0.0
        var lastLocation: Location? = null
        var lastAltitude: Double? = null

        puntos.distinct().forEach { (lat, lon, alt) ->
            val currentLocation = Location("point").apply {
                latitude = lat
                longitude = lon
                altitude = alt
            }

            if (lastLocation != null) {
                totalDistance += lastLocation!!.distanceTo(currentLocation)
                if (lastAltitude != null && alt > lastAltitude!!) {
                    elevationGain += alt - lastAltitude!!
                }
            }
            lastLocation = currentLocation
            lastAltitude = alt
        }

        val totalDistanceKm = totalDistance / 1000.0
        val tiempoTotal = endTime - startTime
        val totalTimeSeconds = tiempoTotal / 1000.0
        val velocidad_promedio = if (totalTimeSeconds > 0) (totalDistanceKm / (totalTimeSeconds / 3600.0)) else 0.0

        // Registros de depuración
        Log.d("CalculateStatistics", "StartTime: $startTime, EndTime: $endTime")
        Log.d("CalculateStatistics", "TotalDistanceKm: $totalDistanceKm")
        Log.d("CalculateStatistics", "TiempoTotal (ms): $tiempoTotal")
        Log.d("CalculateStatistics", "TiempoTotal (s): $totalTimeSeconds")
        Log.d("CalculateStatistics", "VelocidadPromedio: $velocidad_promedio")

        return EstadisticasData(totalDistanceKm, tiempoTotal, velocidad_promedio, elevationGain)
    }

    data class EstadisticasData(
        val distancia: Double,
        val tiempoTotal: Long,
        val velocidad_promedio: Double,
        val desnivelAcumulado: Double
    )

    fun getEstadisticasByRouteId(routeId: Long): EstadisticasData? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ESTADISTICAS,
            arrayOf(
                COLUMN_ESTADISTICAS_DISTANCIA,
                COLUMN_ESTADISTICAS_TIEMPO_TOTAL,
                COLUMN_ESTADISTICAS_VELOCIDAD_PROMEDIO,
                COLUMN_ESTADISTICAS_DESNIVEL_ACUMULADO
            ),
            "$COLUMN_ESTADISTICAS_RUTA_ID = ?",
            arrayOf(routeId.toString()),
            null,
            null,
            null
        )

        return try {
            if (cursor.moveToFirst()) {
                val distancia =
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_ESTADISTICAS_DISTANCIA))
                val tiempoTotal =
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ESTADISTICAS_TIEMPO_TOTAL))
                val velocidad_promedio = cursor.getDouble(
                    cursor.getColumnIndexOrThrow(COLUMN_ESTADISTICAS_VELOCIDAD_PROMEDIO)
                )
                val desnivelAcumulado = cursor.getDouble(
                    cursor.getColumnIndexOrThrow(COLUMN_ESTADISTICAS_DESNIVEL_ACUMULADO)
                )
                EstadisticasData(distancia, tiempoTotal, velocidad_promedio, desnivelAcumulado)
            } else {
                null
            }
        } finally {
            cursor.close()
        }
    }

    fun insertPuntoInteres(
        rutaId: Long,
        lat: Double,
        lon: Double,
        comentario: String,
        imagenPath: String?,
        userImagenPath: String? // Añadido el nuevo parámetro
    ) {
        if (rutaId == -1L) {
            Log.e("DatabaseHelper", "insertPuntoInteres: rutaId is invalid (-1)")
            return
        }

        Log.d(
            "DatabaseHelper",
            "insertPuntoInteres: rutaId = $rutaId, lat = $lat, lon = $lon, comentario = $comentario, imagenPath = $imagenPath, userImagenPath = $userImagenPath"
        )

        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PUNTOS_INTERES_RUTA_ID, rutaId)
            put(COLUMN_PUNTOS_INTERES_LATITUD, lat)
            put(COLUMN_PUNTOS_INTERES_LONGITUD, lon)
            put(COLUMN_PUNTOS_INTERES_COMENTARIO, comentario)
            put(COLUMN_PUNTOS_INTERES_IMAGEN_URL, imagenPath)
            put(COLUMN_PUNTOS_INTERES_USER_IMAGEN_URL, userImagenPath) // Añadido el nuevo valor
        }

        try {
            val insertedRowId = db.insert(TABLE_PUNTOS_INTERES, null, values)
            Log.d("DatabaseHelper", "insertPuntoInteres: Inserted row ID = $insertedRowId")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error inserting PuntoInteres: ${e.message}")
        }
    }

    fun getPuntosInteresByRouteId(routeId: Long): List<PuntoInteres> {
        Log.d("DatabaseHelper", "getPuntosInteresByRouteId: routeId = $routeId")
        val pois = mutableListOf<PuntoInteres>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PUNTOS_INTERES,
            null,
            "$COLUMN_PUNTOS_INTERES_RUTA_ID = ?",
            arrayOf(routeId.toString()),
            null,
            null,
            null
        )

        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PUNTOS_INTERES_ID))
                val latitud = cursor.getDouble(cursor.getColumnIndexOrThrow(
                    COLUMN_PUNTOS_INTERES_LATITUD
                ))
                val longitud = cursor.getDouble(cursor.getColumnIndexOrThrow(
                    COLUMN_PUNTOS_INTERES_LONGITUD
                ))
                val comentario = cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_PUNTOS_INTERES_COMENTARIO
                ))
                val photoUrl = cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_PUNTOS_INTERES_IMAGEN_URL
                ))
                val userPhotoUrl = cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_PUNTOS_INTERES_USER_IMAGEN_URL
                )) // Añadida nueva variable

                val poi = PuntoInteres(id, routeId, latitud, longitud, comentario, photoUrl, userPhotoUrl) // Modificado para incluir userPhotoUrl
                pois.add(poi)
                Log.d("DatabaseHelper", "getPuntosInteresByRouteId: poi = $poi")
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error en getPuntosInteresByRouteId: ${e.message}")
        } finally {
            cursor.close()
            Log.d("DatabaseHelper", "getPuntosInteresByRouteId: pois.size = ${pois.size}")
        }
        return pois
    }

    fun getAllRoutes(): List<Route> {
        val routes = mutableListOf<Route>()
        val db = readableDatabase
        val cursor = db.query(TABLE_RUTAS, null, null, null, null, null, null)

        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_RUTA_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RUTA_NOMBRE))
                val date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RUTA_FECHA))
                val tipoRuta = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RUTA_TIPO))
                routes.add(Route(id, name, date, tipoRuta))
            }
        } catch (e: Exception) {
            Log.e("Database", "Error getAllRoutes: ${e.message}")
        } finally {
            cursor.close()
        }
        return routes
    }

    fun getRoutePoints(routeId: Long): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PUNTOS_RUTA, null, "$COLUMN_PUNTOS_RUTA_RUTA_ID = ?", arrayOf(routeId.toString()), null, null, null)

        try {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    try {
                        val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(
                            COLUMN_PUNTOS_RUTA_LATITUD
                        ))
                        val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(
                            COLUMN_PUNTOS_RUTA_LONGITUD
                        ))
                        val alt = cursor.getDouble(cursor.getColumnIndexOrThrow(
                            COLUMN_PUNTOS_RUTA_ALTURA
                        )) // Obtener la altitud
                        val geoPoint = GeoPoint(lat, lon)
                        geoPoint.altitude = alt; //asignar la altura al geopoint.
                        points.add(geoPoint)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Database", "Error getRoutePoints: ${e.message}")
        } finally {
            cursor?.close()
        }
        return points
    }

    private fun cursorHasRequiredColumns(cursor: android.database.Cursor, requiredColumns: Array<String>): Boolean {
        val columnNames = cursor.columnNames
        return requiredColumns.all { columnNames.contains(it) }
    }

    fun getRouteById(routeId: Long): Route? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RUTAS,
            null,
            "id = ?",
            arrayOf(routeId.toString()),
            null, null, null
        )

        try {
            val requiredColumns = arrayOf("id", "nombre", "fecha", "tipo_ruta")
            if (cursor.moveToFirst() && cursorHasRequiredColumns(cursor, requiredColumns)) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("nombre"))
                val date = cursor.getString(cursor.getColumnIndexOrThrow("fecha"))
                val tipoRuta = cursor.getString(cursor.getColumnIndexOrThrow("tipo_ruta"))

                return Route(id, name, date, tipoRuta)
            } else {
                Log.e("Database", "Column names are incorrect or missing")
                return null
            }
        } catch (e: Exception) {
            Log.e("Database", "Error retrieving route by id: ${e.message}")
            return null
        } finally {
            cursor.close()
        }
    }

    fun deleteRoute(routeId: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_RUTAS, "$COLUMN_RUTA_ID = ?", arrayOf(routeId.toString()))
    }

    fun insertInsignia(nombre: String, fechaObtencion: String, rutaId: Long?, tipoInsignia: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_INSIGNIAS_NOMBRE, nombre)
            put(COLUMN_INSIGNIAS_FECHA_OBTENCION, fechaObtencion)
            rutaId?.let { put(COLUMN_INSIGNIAS_RUTA_ID, it) }
            put(COLUMN_INSIGNIAS_TIPO, tipoInsignia)
        }
        db.insert(TABLE_INSIGNIAS, null, values)
    }

    fun getInsignias(): List<Insignia> {
        val insignias = mutableListOf<Insignia>()
        val db = readableDatabase
        val cursor = db.query(TABLE_INSIGNIAS, null, null, null, null, null, null)

        try {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_INSIGNIAS_ID))
                val nombre = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_INSIGNIAS_NOMBRE))
                val fechaObtencion = cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_INSIGNIAS_FECHA_OBTENCION
                ))
                val rutaId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_INSIGNIAS_RUTA_ID))
                val tipoInsignia = cursor.getString(cursor.getColumnIndexOrThrow(
                    COLUMN_INSIGNIAS_TIPO
                ))

                insignias.add(Insignia(id, nombre, fechaObtencion, if (rutaId != 0L) rutaId else null, tipoInsignia))
            }
        } finally {
            cursor.close()
        }
        return insignias
    }

    fun obtenerTotalPuntosInteresConInfoEcologica(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM PuntosInteres WHERE comentario IS NOT NULL AND (imagen_url IS NOT NULL OR user_imagen_url IS NOT NULL)",
            null
        )

        var total = 0
        if (cursor.moveToFirst()) {
            total = cursor.getInt(0)
        }

        cursor.close()
        return total
    }

    fun updatePoiPhotoUrl(poiId: Long, photoUrl: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PUNTOS_INTERES_IMAGEN_URL, photoUrl)
        }
        db.update(TABLE_PUNTOS_INTERES, values, "$COLUMN_PUNTOS_INTERES_ID = ?", arrayOf(poiId.toString()))
        db.close()
    }

    // ─── Perfil y recomendaciones ────────────────────────────────────────────

    data class PerfilUsuario(
        val tipoRutaDominante: String?,
        val distanciaMedia: Double,
        val velocidadMedia: Double,
        val desnivelMedio: Double,
        val tiempoMedioMs: Long,
        val totalRutas: Int
    )

    data class ResumenProfesional(
        val totalRutas: Int,
        val distanciaTotal: Double,
        val distanciaMedia: Double,
        val tiempoMedioMs: Long,
        val velocidadMedia: Double,
        val tipoDominante: String?,
        val totalPois: Int,
        val totalInsignias: Int
    )

    fun getPerfilUsuario(): PerfilUsuario {
        val db = readableDatabase
        val cursorStats = db.rawQuery(
            """
            SELECT COUNT(r.id), AVG(e.distancia), AVG(e.velocidad_promedio),
                   AVG(e.desnivel_acumulado), AVG(e.tiempo_total)
            FROM Rutas r LEFT JOIN Estadisticas e ON r.id = e.ruta_id
            """.trimIndent(), null
        )
        var totalRutas = 0
        var distanciaMedia = 0.0
        var velocidadMedia = 0.0
        var desnivelMedio = 0.0
        var tiempoMedio = 0L
        if (cursorStats.moveToFirst()) {
            totalRutas = cursorStats.getInt(0)
            distanciaMedia = cursorStats.getDouble(1)
            velocidadMedia = cursorStats.getDouble(2)
            desnivelMedio = cursorStats.getDouble(3)
            tiempoMedio = cursorStats.getLong(4)
        }
        cursorStats.close()

        val cursorTipo = db.rawQuery(
            "SELECT tipo_ruta FROM Rutas WHERE tipo_ruta IS NOT NULL GROUP BY tipo_ruta ORDER BY COUNT(*) DESC LIMIT 1",
            null
        )
        var tipoDominante: String? = null
        if (cursorTipo.moveToFirst()) tipoDominante = cursorTipo.getString(0)
        cursorTipo.close()

        return PerfilUsuario(tipoDominante, distanciaMedia, velocidadMedia, desnivelMedio, tiempoMedio, totalRutas)
    }

    fun getRecomendaciones(perfil: PerfilUsuario): List<Route> {
        if (perfil.totalRutas == 0) return emptyList()
        val db = readableDatabase
        val margen = if (perfil.distanciaMedia > 0) perfil.distanciaMedia * 0.5 else 999.0
        val tipoFiltro = if (perfil.tipoRutaDominante != null)
            "AND r.tipo_ruta = '${perfil.tipoRutaDominante}'"
        else ""
        val cursor = db.rawQuery(
            """
            SELECT r.id, r.nombre, r.fecha, r.tipo_ruta
            FROM Rutas r
            INNER JOIN Estadisticas e ON r.id = e.ruta_id
            WHERE e.distancia BETWEEN ${perfil.distanciaMedia - margen} AND ${perfil.distanciaMedia + margen}
            $tipoFiltro
            ORDER BY ABS(e.distancia - ${perfil.distanciaMedia}) ASC
            LIMIT 15
            """.trimIndent(), null
        )
        val routes = mutableListOf<Route>()
        try {
            while (cursor.moveToNext()) {
                routes.add(Route(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)))
            }
        } finally {
            cursor.close()
        }
        return routes
    }

    fun getResumenProfesional(): ResumenProfesional {
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT COUNT(r.id), SUM(e.distancia), AVG(e.distancia),
                   AVG(e.tiempo_total), AVG(e.velocidad_promedio)
            FROM Rutas r LEFT JOIN Estadisticas e ON r.id = e.ruta_id
            """.trimIndent(), null
        )
        var totalRutas = 0
        var distanciaTotal = 0.0
        var distanciaMedia = 0.0
        var tiempoMedio = 0L
        var velocidadMedia = 0.0
        if (cursor.moveToFirst()) {
            totalRutas = cursor.getInt(0)
            distanciaTotal = cursor.getDouble(1)
            distanciaMedia = cursor.getDouble(2)
            tiempoMedio = cursor.getLong(3)
            velocidadMedia = cursor.getDouble(4)
        }
        cursor.close()

        val cursorTipo = db.rawQuery(
            "SELECT tipo_ruta FROM Rutas WHERE tipo_ruta IS NOT NULL GROUP BY tipo_ruta ORDER BY COUNT(*) DESC LIMIT 1",
            null
        )
        var tipoDominante: String? = null
        if (cursorTipo.moveToFirst()) tipoDominante = cursorTipo.getString(0)
        cursorTipo.close()

        return ResumenProfesional(
            totalRutas, distanciaTotal, distanciaMedia, tiempoMedio, velocidadMedia,
            tipoDominante,
            obtenerTotalPuntosInteresConInfoEcologica(),
            getInsignias().size
        )
    }

    data class Insignia(
        val id: Long,
        val nombre: String,
        val fechaObtencion: String,
        val rutaId: Long?,
        val tipoInsignia: String
    )
}
