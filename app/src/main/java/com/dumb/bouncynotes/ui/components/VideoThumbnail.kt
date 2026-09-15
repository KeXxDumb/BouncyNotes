package com.dumb.bouncynotes.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.dumb.bouncynotes.data.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Miniatura real (un frame decodificado del archivo) por nombre de video,
// cacheada en memoria para toda la sesión — mismo criterio que
// rememberImageAspectRatio en ImageAspectRatio.kt: decodificar un frame con
// MediaMetadataRetriever es bastante más costoso que leer un encabezado de
// imagen, así que no tiene sentido repetirlo cada vez que la misma
// miniatura vuelve a entrar en composición al scrollear (tarjetas de la
// lista, tira de miniaturas del visor).
private val videoThumbnailCache = mutableMapOf<String, ImageBitmap?>()

@Composable
fun rememberVideoThumbnail(context: Context, fileName: String): ImageBitmap? {
    var bitmap by remember(fileName) {
        mutableStateOf(if (videoThumbnailCache.containsKey(fileName)) videoThumbnailCache[fileName] else null)
    }
    LaunchedEffect(fileName) {
        if (videoThumbnailCache.containsKey(fileName)) {
            bitmap = videoThumbnailCache[fileName]
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) { decodeVideoThumbnail(context, fileName) }
        videoThumbnailCache[fileName] = decoded
        bitmap = decoded
    }
    return bitmap
}

private fun decodeVideoThumbnail(context: Context, fileName: String): ImageBitmap? {
    val file = File(ImageStorage.imagesDir(context), fileName)
    if (!file.exists()) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        // getFrameAtTime(0): alcanza con el primer frame, no hace falta uno
        // "representativo" del medio del video para una miniatura chica.
        retriever.getFrameAtTime(0)?.asImageBitmap()
    } catch (e: Exception) {
        // Archivo corrupto, códec no soportado por el extractor de
        // metadata, etc. — se cachea el null igual (ver arriba) para no
        // reintentar en cada recomposición contra un archivo que ya
        // sabemos que falla.
        null
    } finally {
        // MediaMetadataRetriever implementa Closeable/AutoCloseable recién
        // desde API 29 — se llama a release() a mano (existe desde
        // siempre) en vez de usar use{}, para no romper el minSdk 23.
        try {
            retriever.release()
        } catch (e: Exception) {
        }
    }
}
