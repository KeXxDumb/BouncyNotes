package com.dumb.bouncynotes.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

// Copia una imagen/gif/video de una nota hacia el almacenamiento público del
// teléfono (Pictures o Movies), para que quede disponible fuera de la app
// (por ejemplo, para compartirla o simplemente conservarla si se borra la
// nota). Es la función "guardar en el dispositivo" del visor a pantalla
// completa.
object MediaStorageExporter {

    // API 29+ (Android 10, "almacenamiento con ámbito"): se usa MediaStore,
    // que no necesita ningún permiso especial para que la propia app guarde
    // sus propios archivos en una carpeta pública.
    // API 23-28: MediaStore para escribir en la carpeta pública requiere el
    // permiso WRITE_EXTERNAL_STORAGE (se pide antes de llamar a esta función,
    // ver ImageViewerScreen).
    fun saveToDevice(context: Context, sourceFile: File, isVideo: Boolean): Boolean {
        if (!sourceFile.exists()) return false
        return try {
            val mimeType = guessMimeType(sourceFile.name, isVideo)
            val collection: android.net.Uri
            val relativePath: String
            if (isVideo) {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                relativePath = Environment.DIRECTORY_MOVIES + "/BouncyNotes"
            } else {
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                relativePath = Environment.DIRECTORY_PICTURES + "/BouncyNotes"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun guessMimeType(fileName: String, isVideo: Boolean): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            isVideo -> when (ext) {
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                "mkv" -> "video/x-matroska"
                else -> "video/mp4"
            }
            ext == "gif" -> "image/gif"
            ext == "png" -> "image/png"
            else -> "image/jpeg"
        }
    }
}
