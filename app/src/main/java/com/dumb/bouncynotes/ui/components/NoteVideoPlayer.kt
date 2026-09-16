package com.dumb.bouncynotes.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay
import java.io.File

private val SPEED_OPTIONS = listOf(0.5f, 1f, 1.5f, 2f)

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

// Reproductor de video embebido — vive SOLO en el visor a pantalla completa
// (ImageViewerScreen). El editor y el modo lectura de la nota muestran una
// miniatura estática (ver VideoThumbnail.kt) y abren este reproductor recién
// al tocarla, igual que ya funcionaba para las imágenes.
//
// useController = false: el controlador NATIVO de ExoPlayer (el que trae
// PlayerView por default) se reemplaza ACÁ ABAJO por una barra propia hecha
// con composables de Compose. Se sacó el nativo por dos motivos puntuales:
// 1. Traía, sin poder sacarlos por separado, botones de siguiente/anterior
//    (sin sentido: cada reproductor tiene un solo MediaItem, no hay a dónde
//    saltar) y un ícono de "engranaje" con un selector de pista de audio —
//    en una app de notas, ninguna nota va a tener un video con varias pistas
//    de audio para elegir, así que esa opción solo agregaba ruido.
// 2. La velocidad de reproducción quedaba escondida adentro de ese mismo
//    engranaje, en vez de estar a mano junto al resto de los controles.
@Composable
fun NoteVideoPlayer(
    fileName: String,
    modifier: Modifier = Modifier,
    // En el visor (con varias páginas swipeables), indica si esta página es
    // la que está visible ahora mismo: al pasar a otra página se pausa el
    // video en vez de seguir sonando de fondo.
    isActive: Boolean = true,
    // Arranca en silencio: un video adentro de una nota no debería sonar
    // solo apenas se abre — el botón de la barra de abajo lo activa a mano.
    startMuted: Boolean = true,
    // Opcionales: si se pasan, aparecen como botones en la misma barra que
    // el resto de los controles (junto a velocidad y mute). null los oculta
    // — el editor, si llegara a usar este composable, no tiene "guardar en
    // el dispositivo" ni "eliminar" en ese contexto.
    onSaveToDevice: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var errorMessage by remember(fileName) { mutableStateOf<String?>(null) }
    var isMuted by remember(fileName) { mutableStateOf(startMuted) }
    var isPlaying by remember(fileName) { mutableStateOf(false) }
    var positionMs by remember(fileName) { mutableStateOf(0L) }
    var durationMs by remember(fileName) { mutableStateOf(0L) }
    var speed by remember(fileName) { mutableStateOf(1f) }

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

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        durationMs = duration.coerceAtLeast(0L)
                    }
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
    // No hay un Flow/callback de posición en ExoPlayer — se sondea a mano
    // cada 300ms para mover la barra de progreso. No hace falta condicionar
    // esto a isPlaying: en pausa la posición simplemente no cambia entre una
    // lectura y la siguiente, así que no hace nada de más.
    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(300)
        }
    }
    // Sin esto, el reproductor sigue vivo (y consumiendo batería/memoria) aunque
    // el usuario ya haya salido de la pantalla o pasado a otra página del pager.
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                // Tocar el video (fuera de la barra de controles) alterna
                // play/pausa — antes esto lo resolvía el controlador nativo
                // de ExoPlayer; al sacarlo (ver comentario de arriba) hay
                // que reponer manualmente al menos este gesto básico.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }
        )
        // Antes de esto, un video que fallaba se veía IDÉNTICO a uno que
        // todavía no cargó: pantalla negra sin controles que respondan.
        // Este mensaje es lo que hay que buscar en logcat (errorCodeName)
        // si el video sigue sin reproducirse.
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
        // Barra de controles propia, pegada abajo del video (la cinta de
        // miniaturas de todo el contenido de la nota, si la hay, va DEBAJO
        // de este reproductor entero — eso lo arma ImageViewerScreen, este
        // composable no sabe nada de esa cinta).
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMs(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { v -> exoPlayer.seekTo(v.toLong()); positionMs = v.toLong() },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                Text(formatMs(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White
                    )
                }
                TextButton(onClick = {
                    val next = SPEED_OPTIONS[(SPEED_OPTIONS.indexOf(speed) + 1) % SPEED_OPTIONS.size]
                    speed = next
                    exoPlayer.setPlaybackSpeed(next)
                }) {
                    Text(
                        "${speed}x".replace(".0x", "x"),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Box(modifier = Modifier.weight(1f))
                IconButton(onClick = { isMuted = !isMuted }) {
                    Icon(
                        if (isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                        tint = Color.White
                    )
                }
                if (onSaveToDevice != null) {
                    IconButton(onClick = onSaveToDevice) {
                        Icon(Icons.Filled.Download, contentDescription = "Guardar en el dispositivo", tint = Color.White)
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = Color.White)
                    }
                }
            }
        }
    }
}
