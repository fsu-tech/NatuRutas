package com.example.gpxeditor.model.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.gpxeditor.R

class MiServicio : Service() {

    private val CHANNEL_ID = "MiCanal"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mi App Activa")
            .setContentText("La app se está ejecutando en segundo plano")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(1, notification)

        return START_STICKY
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Canal de Servicio",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}