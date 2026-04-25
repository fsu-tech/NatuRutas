package com.example.gpxeditor.controller

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.gpxeditor.R
import com.example.gpxeditor.model.database.DatabaseHelper
import com.google.firebase.firestore.FirebaseFirestore

class RecommendationsActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recommendations)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Rutas para ti"

        dbHelper = DatabaseHelper(this)
        dbHelper.registrarEvento("RutasParaTi", "abrir_pantalla", null)

        val tvPerfil = findViewById<TextView>(R.id.tvPerfilResumen)
        val llRecomendaciones = findViewById<LinearLayout>(R.id.llRecomendaciones)
        val tvSinDatos = findViewById<TextView>(R.id.tvSinDatos)
        val spinnerCiudad = findViewById<Spinner>(R.id.spinnerCiudad)

        // Por ahora solo Granada, pero preparado para más
        val ciudades = listOf("Granada") // Para añadir más, solo agregar aquí, ej: listOf("Granada", "Madrid")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ciudades)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCiudad.adapter = adapter

        // Ocultar recomendaciones hasta seleccionar ciudad
        llRecomendaciones.removeAllViews()
        tvSinDatos.visibility = View.VISIBLE
        tvSinDatos.text = "Selecciona una ciudad para ver recomendaciones."

        spinnerCiudad.setSelection(0)
        spinnerCiudad.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val ciudadSeleccionada = ciudades[position]
                mostrarRecomendaciones(ciudadSeleccionada, tvPerfil, llRecomendaciones, tvSinDatos)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })
    }

    private fun mostrarRecomendaciones(
        ciudad: String,
        tvPerfil: TextView,
        llRecomendaciones: LinearLayout,
        tvSinDatos: TextView
    ) {
        llRecomendaciones.removeAllViews()
        val perfil = dbHelper.getPerfilUsuario()

        if (perfil.totalRutas == 0) {
            tvSinDatos.visibility = View.VISIBLE
            tvPerfil.text = "Aún no tienes rutas guardadas. Sal a explorar y vuelve aquí."
            return
        }

        val tiempoMedioMin = perfil.tiempoMedioMs / 60000
        tvPerfil.text = buildString {
            appendLine("Tu perfil de actividad:")
            append("• Tipo dominante: ${perfil.tipoRutaDominante ?: "variado"}")
            appendLine()
            append("• Distancia media: ${"%.2f".format(perfil.distanciaMedia)} km")
            appendLine()
            append("• Velocidad media: ${"%.1f".format(perfil.velocidadMedia)} km/h")
            appendLine()
            append("• Desnivel medio: ${"%.0f".format(perfil.desnivelMedio)} m")
            appendLine()
            append("• Tiempo medio: $tiempoMedioMin min")
        }

        tvSinDatos.visibility = View.VISIBLE
        tvSinDatos.text = "Buscando tus rutas en Firestore…"

        // Obtener usuario actual de preferencias
        val prefs = getSharedPreferences(DatabaseHelper.PREFS_TELEMETRIA, MODE_PRIVATE)
        val usuario = prefs.getString(DatabaseHelper.KEY_USUARIO, "desconocido")

        val tipo = perfil.tipoRutaDominante
        val hayPerfil = perfil.distanciaMedia > 2.0
        val minDist = if (hayPerfil) perfil.distanciaMedia * 0.5 else 0.0
        val maxDist = if (hayPerfil) perfil.distanciaMedia * 2.0 else Double.MAX_VALUE

        FirebaseFirestore.getInstance()
            .collection("rutas")
            .whereEqualTo("usuario", usuario)
            .get()
            .addOnSuccessListener { snapshot ->
                val rutas = if (!hayPerfil) {
                    snapshot.documents.take(10)
                } else {
                    val filtradas = snapshot.documents.filter { doc ->
                        val dist = doc.getDouble("distancia") ?: 0.0
                        val docTipo = doc.getString("tipoRuta") ?: ""
                        val tipoOk = tipo == null || docTipo.lowercase() == tipo.lowercase()
                        val distOk = dist in minDist..maxDist
                        tipoOk && distOk
                    }.take(10)
                    if (filtradas.isEmpty()) snapshot.documents.take(10) else filtradas
                }

                tvSinDatos.visibility = View.GONE

                if (rutas.isEmpty()) {
                    tvSinDatos.visibility = View.VISIBLE
                    tvSinDatos.text = "Sin rutas para este usuario."
                } else {
                    rutas.forEach { doc ->
                        val nombre = doc.getString("nombre") ?: "Ruta sin nombre"
                        val docTipo = doc.getString("tipoRuta") ?: "—"
                        val dist = doc.getDouble("distancia")?.let { "${"%.1f".format(it)} km" } ?: "—"
                        val fecha = doc.getString("fecha") ?: "—"

                        val tvRuta = TextView(this).apply {
                            text = "📍 $nombre\n   $docTipo · $dist\n   Fecha: $fecha"
                            textSize = 15f
                            setPadding(0, 16, 0, 16)
                        }
                        llRecomendaciones.addView(tvRuta)

                        val divider = View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 1
                            )
                            setBackgroundColor(0xFFDDDDDD.toInt())
                        }
                        llRecomendaciones.addView(divider)
                    }
                    dbHelper.registrarEvento("RutasParaTi", "recomendaciones_mostradas", rutas.size.toString())
                }

            }
            .addOnFailureListener { e ->
                tvSinDatos.visibility = View.VISIBLE
                tvSinDatos.text = "Error: ${e.message}"
            }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
