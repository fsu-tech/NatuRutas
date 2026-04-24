package com.example.gpxeditor.model.entities

data class PuntoInteres(
    val id: Long,
    val routeId: Long,
    val latitud: Double,
    val longitud: Double,
    val comentario: String,
    val imagenUrl: String?,
    val userImagenUrl: String?
)
