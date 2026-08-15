package com.example.notes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object ImageStorage {

    fun imagesDir(context: Context): File {
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun newFileName(): String = "img_${UUID.randomUUID()}.jpg"

    // Copia el archivo tal cual, sin recomprimir (mejor calidad para referencias de arte).
    fun copyFromUri(context: Context, uri: Uri): String? {
        return try {
            val fileName = newFileName()
            val outFile = File(imagesDir(context), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    // Decodifica y recomprime a la calidad indicada (ahorra espacio).
    fun compressFromUri(context: Context, uri: Uri, quality: Int): String? {
        return try {
            val fileName = newFileName()
            val outFile = File(imagesDir(context), fileName)
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                outFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
                bitmap.recycle()
                fileName
            } else {
                copyFromUri(context, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun compressInPlace(context: Context, fileName: String, quality: Int) {
        try {
            val file = File(imagesDir(context), fileName)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
            bitmap.recycle()
        } catch (e: Exception) {
        }
    }

    fun createCaptureFile(context: Context): Pair<File, Uri> {
        val fileName = newFileName()
        val file = File(imagesDir(context), fileName)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return file to uri
    }

    fun deleteFile(context: Context, fileName: String) {
        try {
            File(imagesDir(context), fileName).delete()
        } catch (e: Exception) {
        }
    }
}
