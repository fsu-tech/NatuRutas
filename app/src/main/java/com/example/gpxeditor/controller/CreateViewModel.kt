package com.example.gpxeditor.controller
// Archivo eliminado. ViewModel obsoleto.
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.gpxeditor.model.entities.PuntoInteresTemporal
import org.osmdroid.util.GeoPoint

class CreateViewModel : ViewModel() {
    val isRecording = MutableLiveData(false)
    val startTime = MutableLiveData<Long>(0)
    val routePoints = MutableLiveData<MutableList<GeoPoint>>(mutableListOf())
    val puntosInteresTemporales = MutableLiveData<MutableList<PuntoInteresTemporal>>(mutableListOf()) // Corrected line

    fun startRecording() {
        isRecording.value = true
        startTime.value = System.currentTimeMillis()
        routePoints.value?.clear()
        puntosInteresTemporales.value?.clear()
        routePoints.value = mutableListOf()
        puntosInteresTemporales.value = mutableListOf()
    }

    fun stopRecording() {
        isRecording.value = false
    }

    fun addRoutePoint(point: GeoPoint) {
        routePoints.value?.add(point)
        routePoints.value = routePoints.value
    }

    fun addPuntoInteres(punto: PuntoInteresTemporal) {
        puntosInteresTemporales.value?.add(punto)
        puntosInteresTemporales.value = puntosInteresTemporales.value
    }
}