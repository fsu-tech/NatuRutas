package com.example.gpxeditor.model.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PuntoInteresTemporalParcelable(
    val latitud: Double,
    val longitud: Double,
    val comentario: String,
    val imagenPath: String?
) : Parcelable