package com.example.gpxeditor.controller

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.gpxeditor.view.fragments.CompareFragment
import com.example.gpxeditor.view.fragments.CreateFragment
import com.example.gpxeditor.view.fragments.HomeFragment
import com.example.gpxeditor.R
import com.example.gpxeditor.view.fragments.SavedRoutesFragment
import com.example.gpxeditor.model.database.DatabaseHelper
import com.example.gpxeditor.view.fragments.SettingsFragment
import com.example.gpxeditor.model.services.MiServicio
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity(), HomeFragment.NavigationListener, SharedPreferences.OnSharedPreferenceChangeListener {
        private val LOCATION_PERMISSION_CODE = 102
        private var pendingFragment: Fragment? = null
    private lateinit var btnPanelProfesionalAjustes: android.widget.Button

    private val READ_STORAGE_PERMISSION_CODE = 101
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            discardStoppedRecordingDraft()
        }

        btnPanelProfesionalAjustes = findViewById(R.id.btnPanelProfesionalAjustes)
        btnPanelProfesionalAjustes.setOnClickListener {
            startActivity(Intent(this, com.example.gpxeditor.controller.BusinessInsightsActivity::class.java))
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        dbHelper = DatabaseHelper(this)

        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)

        bottomNavigationView.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_compare -> CompareFragment()
                R.id.nav_settings -> SettingsFragment()
                R.id.nav_saved_routes -> SavedRoutesFragment()
                else -> null
            }
            // Mostrar u ocultar el botón Panel profesional según el fragmento
            if (item.itemId == R.id.nav_settings) {
                btnPanelProfesionalAjustes.visibility = android.view.View.VISIBLE
            } else {
                btnPanelProfesionalAjustes.visibility = android.view.View.GONE
            }
            if (fragment != null) {
                val pantalla = when (item.itemId) {
                    R.id.nav_home -> "Inicio"
                    R.id.nav_compare -> "Comparar"
                    R.id.nav_settings -> "Ajustes"
                    R.id.nav_saved_routes -> "MisRutas"
                    else -> "Desconocido"
                }
                dbHelper.registrarEvento(pantalla, "navegar_a_pantalla", null)
                changeFragmentWithConfirmation(fragment)
            }
            true
        }

        if (savedInstanceState == null) {
            // Siempre mostrar primero Inicio
            val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
            bottomNavigationView.selectedItemId = R.id.nav_home
            loadFragment(com.example.gpxeditor.view.fragments.HomeFragment())
            android.os.Handler(mainLooper).postDelayed({
                android.widget.Toast.makeText(this, "Pulsa 'Iniciar ruta' para empezar a grabar tu recorrido", android.widget.Toast.LENGTH_LONG).show()
            }, 600)
        }

        handleIntent()
        aplicarPreferenciasIniciales()
    }

    /**
     * Conserva una grabación activa para poder recuperarla si la app se cierra por accidente,
     * pero descarta una ruta que el usuario ya detuvo y no llegó a guardar.
     *
     * Se ejecuta únicamente en un arranque nuevo de MainActivity, no al cambiar de pestaña ni
     * durante una recreación de la pantalla, para que la ruta detenida pueda guardarse mientras
     * la sesión actual siga abierta.
     */
    private fun discardStoppedRecordingDraft() {
        val routePreferences = getSharedPreferences("ruta_data", MODE_PRIVATE)
        if (routePreferences.getBoolean("isRecordingHome", false)) return

        routePreferences.edit()
            .remove("recording_points")
            .remove("recording_elevations")
            .remove("recording_waypoints")
            .remove("isPausedHome")
            .remove("startTimeHome")
            .remove("endTimeHome")
            .remove("pauseStartTimeHome")
            .remove("totalPauseTimeHome")
            .apply()
    }

    private fun loadFragment(fragment: Fragment, tag: String? = null) {
        val transaction = supportFragmentManager.beginTransaction()
        if (tag != null) {
            transaction.replace(R.id.fragment_container, fragment, tag)
        } else {
            transaction.replace(R.id.fragment_container, fragment)
        }
        transaction.commit()
    }

    override fun onResume() {
        super.onResume()
        startService(Intent(this, MiServicio::class.java))
    }

    override fun onPause() {
        super.onPause()
        stopService(Intent(this, MiServicio::class.java))
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setMessage("¿Seguro que quieres salir?")
            .setCancelable(false)
            .setPositiveButton("Sí") { _, _ ->
                super.onBackPressed() // Cierra la aplicación
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun handleIntent(sourceIntent: Intent = intent) {
        val action = sourceIntent.action
        val data = when (action) {
            Intent.ACTION_VIEW -> sourceIntent.data
            Intent.ACTION_SEND -> sourceIntent.getSharedUri()
            else -> null
        }

        if (data != null) {
            passUriToFragment(data)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.getSharedUri(): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } ?: clipData?.getItemAt(0)?.uri
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                intent.data?.let { passUriToFragment(it) }
            } else {
                // Permiso denegado, muestra un mensaje al usuario
            }
        }
        if (requestCode == LOCATION_PERMISSION_CODE) {
            val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(this, "Permiso concedido. Ahora puedes pulsar 'Crear ruta' para empezar.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(this, "Permiso de ubicación denegado. No se puede grabar rutas.", android.widget.Toast.LENGTH_LONG).show()
            }
            bottomNavigationView.selectedItemId = R.id.nav_home
            loadFragment(com.example.gpxeditor.view.fragments.HomeFragment())
            pendingFragment = null
        }
    }

    private fun passUriToFragment(uri: Uri) {
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.selectedItemId = R.id.nav_home
        supportFragmentManager.executePendingTransactions()

        val homeFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? HomeFragment
            ?: HomeFragment().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, it)
                    .commitNow()
            }
        homeFragment.openGpxFile(uri)
    }

    fun changeFragmentWithConfirmation(fragment: Fragment) {
        Log.d("MainActivity", "changeFragmentWithConfirmation: fragment = $fragment")

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (currentFragment) {
            is CreateFragment -> currentFragment.onNavigationAttempt(this, fragment)
            is HomeFragment -> currentFragment.onNavigationAttempt(this, fragment)
            else -> loadFragment(fragment)
        }
    }

    override fun navigateToFragment(fragment: Fragment) {
        loadFragment(fragment)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "dark_mode" -> aplicarModoOscuro()
            "user_name" -> aplicarNombreUsuario()
            "notifications_enabled" -> aplicarNotificaciones()
        }
    }

    private fun aplicarPreferenciasIniciales() {
        aplicarModoOscuro()
        aplicarNombreUsuario()
        aplicarNotificaciones()
    }

    private fun aplicarModoOscuro() {
        val darkModeEnabled = sharedPreferences.getBoolean("dark_mode", false)
        if (darkModeEnabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun aplicarNombreUsuario() {
        val userName = sharedPreferences.getString("user_name", "")
        Log.d("MainActivity", "Nombre de usuario: $userName")
    }

    private fun aplicarNotificaciones() {
        val notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true)
        Log.d("MainActivity", "Notificaciones activadas: $notificationsEnabled")
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
}
