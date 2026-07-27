package com.example.gpxeditor.view.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.BatteryManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Panel de ayuda iniciado siempre por el usuario. No realiza llamadas ni envía mensajes
 * automáticamente: abre el marcador, la aplicación de SMS o el selector para compartir.
 */
object RouteHelpDialog {
    private const val LOCATION_PERMISSION_CODE = 102

    fun show(
        fragment: Fragment,
        fusedLocationClient: FusedLocationProviderClient,
        cachedLocation: Location?,
        onLocationResolved: (Location) -> Unit,
        onEvent: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        var resolvedLocation = cachedLocation
        val locationText = cachedLocation?.let {
            String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude)
        } ?: "Buscando ubicación GPS…"
        val density = context.resources.displayMetrics.density
        val padding = (20 * density).toInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, (8 * density).toInt(), padding, 0)
        }
        val status = TextView(context).apply {
            text = "Tu posición: $locationText\nBatería: ${batteryLevel(context)} %"
            textSize = 16f
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        container.addView(status)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Ayuda en ruta")
            .setView(container)
            .setNegativeButton("Cerrar", null)
            .create()

        fun addAction(label: String, action: () -> Unit) {
            container.addView(
                MaterialButton(context).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener {
                        dialog.dismiss()
                        action()
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (6 * density).toInt()
                }
            )
        }

        addAction("Compartir mi ubicación") {
            resolveAndShare(fragment, fusedLocationClient, resolvedLocation, false, onLocationResolved, onEvent)
        }
        addAction("Enviar SMS a contactos de confianza") {
            resolveAndShare(fragment, fusedLocationClient, resolvedLocation, true, onLocationResolved, onEvent)
        }
        addAction("Llamar al 112 — emergencias") {
            confirmCall(fragment, "112", "Emergencias", onEvent)
        }
        addAction("Llamar al 062 — Guardia Civil") {
            confirmCall(fragment, "062", "Guardia Civil", onEvent)
        }
        dialog.show()

        if (cachedLocation == null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            resolveCurrentLocation(fusedLocationClient) { location ->
                if (location != null && fragment.isAdded && dialog.isShowing) {
                    resolvedLocation = location
                    onLocationResolved(location)
                    val coordinates = String.format(
                        Locale.US,
                        "%.6f, %.6f",
                        location.latitude,
                        location.longitude
                    )
                    status.text = "Tu posición: $coordinates\nBatería: ${batteryLevel(context)} %"
                } else if (location == null && dialog.isShowing) {
                    status.text = "No se pudo obtener la ubicación. Activa el GPS.\nBatería: ${batteryLevel(context)} %"
                }
            }
        }
    }
    private fun resolveAndShare(
        fragment: Fragment,
        client: FusedLocationProviderClient,
        cachedLocation: Location?,
        trustedContacts: Boolean,
        onLocationResolved: (Location) -> Unit,
        onEvent: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                fragment.requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
            Toast.makeText(context, "Concede la ubicación y vuelve a pulsar Ayuda", Toast.LENGTH_LONG).show()
            return
        }

        if (cachedLocation != null) {
            share(fragment, cachedLocation, trustedContacts, onEvent)
            return
        }

        resolveCurrentLocation(client) { location ->
            if (!fragment.isAdded) return@resolveCurrentLocation
            if (location == null) {
                Toast.makeText(
                    context,
                    "No se pudo obtener la posición. Activa el GPS y vuelve a intentarlo.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                onLocationResolved(location)
                share(fragment, location, trustedContacts, onEvent)
            }
        }
    }

    private fun resolveCurrentLocation(
        client: FusedLocationProviderClient,
        result: (Location?) -> Unit
    ) {
        client.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null) {
                    result(cached)
                } else {
                    val tokenSource = CancellationTokenSource()
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                        .addOnSuccessListener(result)
                        .addOnFailureListener { result(null) }
                }
            }
            .addOnFailureListener { result(null) }
    }

    private fun share(
        fragment: Fragment,
        location: Location,
        trustedContacts: Boolean,
        onEvent: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        val coordinates = String.format(Locale.US, "%.6f,%.6f", location.latitude, location.longitude)
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val message = "Necesito ayuda en mi ruta. Estoy aquí: " +
            "https://maps.google.com/?q=$coordinates\n" +
            "Coordenadas: $coordinates\nHora: $time\nBatería: ${batteryLevel(context)} %"

        val intent = if (trustedContacts) {
            val phones = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("trusted_contact_phones", "")
                .orEmpty()
                .split(',', ';')
                .map { value -> value.trim().filter { it.isDigit() || it == '+' } }
                .filter { it.isNotBlank() }
            if (phones.isEmpty()) {
                Toast.makeText(context, "Añade los teléfonos de confianza en Ajustes", Toast.LENGTH_LONG).show()
                return
            }
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phones.joinToString(";")}")).apply {
                putExtra("sms_body", message)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ayuda en ruta")
                putExtra(Intent.EXTRA_TEXT, message)
            }
        }

        try {
            fragment.startActivity(
                if (trustedContacts) intent else Intent.createChooser(intent, "Compartir ubicación")
            )
            onEvent(if (trustedContacts) "preparar_sms_ubicacion" else "compartir_ubicacion")
        } catch (_: Exception) {
            Toast.makeText(context, "No hay una aplicación disponible para enviar el aviso", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmCall(
        fragment: Fragment,
        number: String,
        service: String,
        onEvent: (String) -> Unit
    ) {
        val context = fragment.requireContext()
        AlertDialog.Builder(context)
            .setTitle("Llamar al $number")
            .setMessage("¿Quieres abrir el teléfono para llamar a $service? Usa este número solo cuando corresponda.")
            .setPositiveButton("Abrir teléfono") { _, _ ->
                try {
                    fragment.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
                    onEvent("abrir_llamada_$number")
                } catch (_: Exception) {
                    Toast.makeText(context, "No se pudo abrir el teléfono", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun batteryLevel(context: Context): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
}
