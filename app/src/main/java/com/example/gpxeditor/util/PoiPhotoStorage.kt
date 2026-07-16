package com.example.gpxeditor.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object PoiPhotoStorage {
    private const val PHOTO_DIRECTORY = "poi_photos"

    fun createPhotoFile(context: Context): File {
        val directory = File(context.filesDir, PHOTO_DIRECTORY).apply { mkdirs() }
        return File(directory, "poi_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
    }

    fun copyToPrivateStorage(context: Context, source: Uri): File {
        val destination = createPhotoFile(context)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "No se pudo abrir la imagen seleccionada" }
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
        return destination
    }

    fun glideModel(reference: String): Any {
        val localFile = File(reference)
        return if (localFile.isAbsolute && localFile.exists()) localFile else reference
    }

    fun openPhoto(context: Context, reference: String) {
        val localFile = File(reference)
        val uri = if (localFile.isAbsolute && localFile.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        } else {
            Uri.parse(reference)
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (localFile.exists()) setDataAndType(uri, "image/*") else data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
