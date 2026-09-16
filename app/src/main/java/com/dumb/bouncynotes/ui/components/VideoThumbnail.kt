package com.dumb.bouncynotes.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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

// Miniatura clicable para un video insertado en una nota: un frame real (o
// el ícono de play sobre negro mientras se decodifica / si falla) que abre
// el visor a pantalla completa al tocar CUALQUIER parte — antes el video se
// embebía directo como reproductor en el editor y en modo lectura (con
// reproducción automática con sonido, ver NoteVideoPlayer.kt), así que
// tocar el video no hacía nada porque ya estaba "abierto"; ahora se
// comporta exactamente igual que una imagen: miniatura + tap para abrir.
@Composable
fun VideoThumbnailPreview(
    context: Context,
    fileName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val thumbnail = rememberVideoThumbnail(context, fileName)
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = "Video",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Icon(
            Icons.Filled.PlayCircle,
            contentDescription = if (thumbnail == null) "Video" else null,
            tint = Color.White,
            modifier = Modifier.size(48.dp)
        )
    }
}
