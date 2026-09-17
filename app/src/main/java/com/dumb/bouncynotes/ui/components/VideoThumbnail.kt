package com.dumb.bouncynotes.ui.components

import android.content.Context
import android.graphics.Bitmap
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

// Miniatura real (un frame decodificado del archivo, reducido a un tamaño
// chico — ver downscaleForThumbnail más abajo) por nombre de video, cacheada
// en memoria para toda la sesión — mismo criterio que rememberImageAspectRatio
// en ImageAspectRatio.kt: decodificar un frame con MediaMetadataRetriever es
// bastante más costoso que leer un encabezado de imagen, así que no tiene
// sentido repetirlo cada vez que la misma miniatura vuelve a entrar en
// composición al scrollear (tarjetas de la lista, tira de miniaturas del
// visor). A diferencia de esa función, acá SÍ hace falta procesar el bitmap
// antes de cachearlo (no solo leerlo): sin reducirlo de tamaño, cada entrada
// de este cache pesaría lo mismo que el video en su resolución original
// (varios MB para 1080p/4K) en vez de los ~200KB de una miniatura.
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
        val fullFrame = retriever.getFrameAtTime(0) ?: return null
        // PROBLEMA DE MEMORIA encontrado: este frame viene a la resolución
        // NATIVA del video (a 1080p, ~8MB sin comprimir; a 4K, ~32MB — un
        // solo Bitmap ARGB_8888 pesa ancho×alto×4 bytes), pero acá nunca se
        // muestra a más de unos pocos cientos de dp (tarjetas de la lista,
        // ícono de play encima). Como además queda cacheado en memoria para
        // toda la sesión (ver videoThumbnailCache más abajo), sin este
        // escalado cada video distinto que el usuario mira suma varios MB
        // permanentes — con unas pocas notas con video en equipos con poca
        // RAM, esto es plata contante para jank o hasta un OOM.
        val scaled = downscaleForThumbnail(fullFrame, maxDimension = 320)
        // Si downscaleForThumbnail devolvió una copia nueva (el frame
        // original era más grande que el máximo), se libera el original de
        // inmediato en vez de esperar al GC — son varios MB que no tiene
        // sentido dejar colgados ni un instante más de lo necesario.
        if (scaled !== fullFrame) fullFrame.recycle()
        scaled.asImageBitmap()
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

// Reduce un bitmap decodificado a, como mucho, maxDimension píxeles de lado
// más largo, preservando la relación de aspecto. Devuelve el MISMO bitmap
// (sin copiar) si ya es más chico que eso, para no gastar tiempo/memoria de
// más en videos que ya vinieron en baja resolución.
private fun downscaleForThumbnail(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val largestSide = maxOf(bitmap.width, bitmap.height)
    if (largestSide <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / largestSide
    val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
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
