package com.dumb.bouncynotes.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dumb.bouncynotes.data.ImageStorage
import java.io.File

// Reproductor de video embebido (con sus propios controles: play/pausa, barra
// de progreso, pantalla completa del propio PlayerView). Vive SOLO en el
// visor a pantalla completa (ImageViewerScreen) — el editor y el modo
// lectura de la nota ahora muestran una miniatura estática (ver
// VideoThumbnail.kt) y abren este reproductor recién al tocarla, igual que
// ya funcionaba para las imágenes. Antes este mismo composable se embebía
// directo en el editor/modo lectura con reproducción automática apenas se
// insertaba o se abría la nota — eso era, a la vez, la causa de que un
// video "no se viera como miniatura, sino que reproducía solo" y del
// crasheo al insertar uno nuevo (una instancia de ExoPlayer arrancando a
// reproducir con sonido en medio de la transición de vuelta del selector de
// medios, sin que el usuario lo pidiera).
@Composable
fun NoteVideoPlayer(
    fileName: String,
    modifier: Modifier = Modifier,
    // En el visor a pantalla completa (con varias páginas swipeables), esto
    // indica si esta página es la que está visible ahora mismo: al pasar a
    // otra página se pausa el video en vez de seguir sonando de fondo.
    isActive: Boolean = true,
    // Arranca en silencio: un video adentro de una nota no debería sonar
    // solo apenas se abre — el botón de arriba a la derecha lo activa a mano.
    startMuted: Boolean = true
) {
    val context = LocalContext.current
    // Antes un fallo acá (archivo corrupto, códec no soportado por el
    // dispositivo, etc.) quedaba en TOTAL silencio: sin Player.Listener no
    // había forma de que el usuario (ni nosotros, debugueando) supiera que
    // algo salió mal — solo se veía un cuadro negro sin controles que
    // reaccionaran, indistinguible de "todavía está cargando".
    var errorMessage by remember(fileName) { mutableStateOf<String?>(null) }
    var isMuted by remember(fileName) { mutableStateOf(startMuted) }
    // remember(fileName): si la nota tiene más de un video, cada uno necesita
    // su propia instancia de ExoPlayer (compartir una sola entre videos
    // distintos haría que cambiar de página del pager, por ejemplo, corte el
    // video equivocado o reproduzca el archivo que no toca).
    val exoPlayer = remember(fileName) {
        ExoPlayer.Builder(context).build().apply {
            val file = File(ImageStorage.imagesDir(context), fileName)
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            volume = if (startMuted) 0f else 1f
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    errorMessage = error.errorCodeName
                }
            })
            prepare()
        }
    }
    // Pausa el video apenas isActive pasa a false (por ejemplo, al pasar de
    // página en el visor), en vez de dejarlo sonando fuera de pantalla.
    LaunchedEffect(isActive) {
        exoPlayer.playWhenReady = isActive
        if (!isActive) exoPlayer.pause()
    }
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }
    // Sin esto, el reproductor sigue vivo (y consumiendo batería/memoria) aunque
    // el usuario ya haya salido de la pantalla o pasado a otra página del pager.
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    // Cada video tiene su PROPIA instancia de ExoPlayer con
                    // un único MediaItem (ver el comentario de arriba) — los
                    // botones de siguiente/anterior del controlador default
                    // de ExoPlayer no tienen a dónde saltar dentro de ESTE
                    // reproductor. La navegación real entre imágenes/videos
                    // de la nota la dan las flechas propias del visor
                    // (ImageViewerScreen), así que estos botones solo
                    // confundían sin hacer nada.
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = { isMuted = !isMuted },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(
                if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        // Antes de esto, un video que fallaba se veía IDÉNTICO a uno que
        // todavía no cargó: pantalla negra sin controles que respondan.
        // Este mensaje es lo que hay que buscar en logcat (errorCodeName)
        // si el video sigue sin reproducirse después de este cambio.
        val message = errorMessage
        if (message != null) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No se pudo reproducir este video ($message)",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
