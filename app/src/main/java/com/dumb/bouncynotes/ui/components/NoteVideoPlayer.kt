package com.dumb.bouncynotes.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dumb.bouncynotes.data.ImageStorage
import java.io.File

// Reproductor de video embebido (con sus propios controles: play/pausa, barra
// de progreso, pantalla completa del propio PlayerView). Se usa tanto en el
// editor de la nota como en el visor a pantalla completa, así que vive en un
// único lugar para no duplicar la configuración de ExoPlayer.
@Composable
fun NoteVideoPlayer(
    fileName: String,
    modifier: Modifier = Modifier,
    // En el visor a pantalla completa (con varias páginas swipeables), esto
    // indica si esta página es la que está visible ahora mismo: al pasar a
    // otra página se pausa el video en vez de seguir sonando de fondo.
    isActive: Boolean = true
) {
    val context = LocalContext.current
    // remember(fileName): si la nota tiene más de un video, cada uno necesita
    // su propia instancia de ExoPlayer (compartir una sola entre videos
    // distintos haría que cambiar de página del pager, por ejemplo, corte el
    // video equivocado o reproduzca el archivo que no toca).
    val exoPlayer = remember(fileName) {
        ExoPlayer.Builder(context).build().apply {
            val file = File(ImageStorage.imagesDir(context), fileName)
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    // Pausa el video apenas isActive pasa a false (por ejemplo, al pasar de
    // página en el visor), en vez de dejarlo sonando fuera de pantalla.
    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.pause()
    }
    // Sin esto, el reproductor sigue vivo (y consumiendo batería/memoria) aunque
    // el usuario ya haya salido de la pantalla o pasado a otra página del pager.
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}
