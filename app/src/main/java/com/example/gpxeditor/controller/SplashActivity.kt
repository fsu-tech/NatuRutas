package com.example.gpxeditor.controller

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.gpxeditor.R
import com.example.gpxeditor.model.database.DatabaseHelper

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val imageView: ImageView = findViewById(R.id.splash_image)
        Glide.with(this).load(R.drawable.naturutas).into(imageView)

        val prefs = getSharedPreferences(DatabaseHelper.PREFS_TELEMETRIA, MODE_PRIVATE)
        val usuarioGuardado = prefs.getString(DatabaseHelper.KEY_USUARIO, null)

        if (usuarioGuardado == null) {
            // Primera vez: pedir nombre tras 1 segundo para que cargue la splash
            handler.postDelayed({ pedirNombre(prefs) }, 1000)
        } else {
            // Ya tiene nombre: generar nueva sesión y continuar
            generarSesionYContinuar(prefs, usuarioGuardado)
        }
    }

    private fun pedirNombre(prefs: android.content.SharedPreferences) {
        val input = EditText(this).apply {
            hint = "Tu nombre o alias"
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle("Bienvenido a NatuRutas")
            .setMessage("Introduce tu nombre para continuar:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Continuar") { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isEmpty()) {
                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                    pedirNombre(prefs) // Volver a preguntar
                } else {
                    // Buscar usuario en Firestore
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    firestore.collection("usuarios")
                        .whereEqualTo("nombre", nombre)
                        .get()
                        .addOnSuccessListener { result ->
                            if (!result.isEmpty) {
                                // Usuario existe, usar su usuarioId
                                val usuarioDoc = result.documents[0]
                                val usuarioId = usuarioDoc.getString("usuarioId") ?: java.util.UUID.randomUUID().toString()
                                prefs.edit()
                                    .putString(DatabaseHelper.KEY_USUARIO, nombre)
                                    .putString(DatabaseHelper.KEY_USUARIO_ID, usuarioId)
                                    .apply()
                                generarSesionYContinuar(prefs, nombre)
                            } else {
                                // Usuario no existe, crear uno nuevo
                                val usuarioId = java.util.UUID.randomUUID().toString()
                                val usuarioMap = hashMapOf(
                                    "nombre" to nombre,
                                    "usuarioId" to usuarioId
                                )
                                firestore.collection("usuarios").add(usuarioMap)
                                    .addOnSuccessListener {
                                        prefs.edit()
                                            .putString(DatabaseHelper.KEY_USUARIO, nombre)
                                            .putString(DatabaseHelper.KEY_USUARIO_ID, usuarioId)
                                            .apply()
                                        generarSesionYContinuar(prefs, nombre)
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Error creando usuario: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error buscando usuario: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .show()
    }

    private fun generarSesionYContinuar(prefs: android.content.SharedPreferences, usuario: String) {
        val sesionId = "${usuario}_${System.currentTimeMillis()}"
        prefs.edit().putString(DatabaseHelper.KEY_SESION, sesionId).apply()

        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
