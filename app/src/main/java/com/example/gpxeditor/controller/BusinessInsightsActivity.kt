package com.example.gpxeditor.controller

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.gpxeditor.R
import com.example.gpxeditor.model.database.DatabaseHelper

class BusinessInsightsActivity : AppCompatActivity() {

    // ── Cambia estos valores por los tuyos ──────────────────────────────────
    private val CONTACTO_EMAIL = "contacto@naturutas.com"
    private val CONTACTO_WHATSAPP = "34600000000"   // sin + ni espacios
    // ────────────────────────────────────────────────────────────────────────

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_business_insights)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Panel Profesional"

        dbHelper = DatabaseHelper(this)
        dbHelper.registrarEvento("PanelProfesional", "abrir_pantalla", null)

        val tvResumen = findViewById<TextView>(R.id.tvResumen)

        val resumen = dbHelper.getResumenProfesional()
        val tiempoMedioMin = resumen.tiempoMedioMs / 60000
        val distTotalKm   = "%.2f".format(resumen.distanciaTotal)
        val distMediaKm   = "%.2f".format(resumen.distanciaMedia)
        val velMedia      = "%.1f".format(resumen.velocidadMedia)

        val resumenPorActividad = buildResumenPorActividad()

        tvResumen.text = buildString {
            appendLine("📊 Actividad registrada:")
            appendLine("  • Rutas totales: ${resumen.totalRutas}")
            appendLine("  • Distancia total: $distTotalKm km")
            appendLine("  • Distancia media por ruta: $distMediaKm km")
            appendLine("  • Tiempo medio por ruta: $tiempoMedioMin min")
            appendLine("  • Velocidad media: $velMedia km/h")
            appendLine("  • Tipo de actividad dominante: ${resumen.tipoDominante ?: "variado"}")
            appendLine("  • Puntos de interés registrados: ${resumen.totalPois}")
            appendLine("  • Insignias obtenidas: ${resumen.totalInsignias}")
            if (resumenPorActividad.isNotBlank()) {
                appendLine()
                appendLine("📈 Desglose por tipo de actividad:")
                append(resumenPorActividad)
            }
        }

        findViewById<Button>(R.id.btnSolicitarInfo).setOnClickListener {
            dbHelper.registrarEvento("PanelProfesional", "click_email_contacto", null)
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$CONTACTO_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "Solicitud de información – NatuRutas")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Hola,\n\nMe interesa conocer más sobre NatuRutas y sus posibilidades para empresas del sector outdoor.\n\nQuedo a la espera de su respuesta.\n\nGracias."
                )
            }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        }

        findViewById<Button>(R.id.btnWhatsApp).setOnClickListener {
            dbHelper.registrarEvento("PanelProfesional", "click_whatsapp_contacto", null)
            val mensaje = Uri.encode("Hola, me interesa conocer más sobre NatuRutas y sus posibilidades para empresas del sector outdoor.")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$CONTACTO_WHATSAPP?text=$mensaje"))
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        }

        dbHelper.registrarEvento("PanelProfesional", "resumen_generado", resumen.totalRutas.toString())
    }

    private fun buildResumenPorActividad(): String {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT r.tipo_ruta, COUNT(*) as cnt, AVG(e.distancia) as dist_media
            FROM Rutas r
            LEFT JOIN Estadisticas e ON r.id = e.ruta_id
            WHERE r.tipo_ruta IS NOT NULL
            GROUP BY r.tipo_ruta
            ORDER BY cnt DESC
            """.trimIndent(), null
        )
        val sb = StringBuilder()
        try {
            while (cursor.moveToNext()) {
                val tipo     = cursor.getString(0) ?: "—"
                val cnt      = cursor.getInt(1)
                val distMedia = cursor.getDouble(2)
                sb.appendLine("  • $tipo: $cnt rutas · ${"%.1f".format(distMedia)} km media")
            }
        } finally {
            cursor.close()
        }
        return sb.toString().trimEnd()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}

