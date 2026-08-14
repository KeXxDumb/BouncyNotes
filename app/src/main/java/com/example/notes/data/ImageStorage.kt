package com.example.notes.data

import android.content.Context
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

    fun copyFromUri(context: Context, uri: Uri): String? {
        return try {
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val outFile = File(imagesDir(context), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun createCaptureFile(context: Context): Pair<File, Uri> {
        val fileName = "img_${UUID.randomUUID()}.jpg"
        val file = File(imagesDir(context), fileName)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return file to uri
    }

    fun deleteFile(path: String) {
        try {
            File(path).delete()
        } catch (e: Exception) {
        }
    }
}
