package com.dumb.bouncynotes.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.ImageStorage
import java.io.File

// Fondo de imagen configurable en Ajustes (monocromático opcional, opacidad,
// desvanecido de bordes) — antes vivía solo adentro de NoteListScreen.kt,
// ahora es compartido: si settings.showBackgroundInNotes está activo,
// NoteEditScreen también lo dibuja detrás de una nota individual.
//
// extraDarkeningAlpha agrega una capa negra PLANA encima de la imagen, aparte
// de settings.backgroundImageOpacity (que es la opacidad de la imagen en sí,
// y se mantiene igual en los dos lugares para que no "cambie de aspecto" al
// entrar a una nota). Adentro de una nota hay bastante más texto para leer
// que en la lista, así que conviene un fondo más apagado ahí — en vez de
// tocar el número que el usuario configuró (que se vería distinto también en
// la lista), se sobrepone este oscurecido fijo aparte, solo en notas.
@Composable
fun NoteBackgroundImage(
    settings: AppSettings,
    context: Context,
    extraDarkeningAlpha: Float = 0f
) {
    val path = settings.backgroundImagePath ?: return
    if (settings.backgroundMonochrome) {
        // BUG reportado: con el modo monocromático activo, mientras la
        // imagen todavía no terminó de cargar (o si algún borde quedara sin
        // cubrir) se veía el color "surface" de la paleta del tema — es el
        // color de la Surface base de MainActivity, que se ve A TRAVÉS
        // mientras no hay nada más pintado encima todavía. En modo
        // monocromático la idea es una foto en blanco y negro sobre un
        // fondo oscuro parejo, así que ese respaldo tiene que ser negro
        // plano siempre, no un color que cambia según el color semilla
        // elegido en Ajustes.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    }
    AsyncImage(
        model = File(ImageStorage.imagesDir(context), path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = if (settings.backgroundMonochrome) {
            ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.Color)
        } else null,
        // graphicsLayer (no el parámetro `alpha` propio de AsyncImage): así
        // desvanece parejo tanto la imagen como el tinte de color, aplicando
        // el alpha como una capa de composición APARTE, después de que la
        // imagen (ya teñida o no) esté completamente dibujada.
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = settings.backgroundImageOpacity)
    )
    if (extraDarkeningAlpha > 0f) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = extraDarkeningAlpha)))
    }
    if (settings.backgroundFade) {
        // Un radialGradient dibuja un círculo, así que en una imagen
        // rectangular solo se nota el desvanecido cerca de las esquinas; los
        // bordes superior/inferior/laterales quedaban casi sin desvanecer.
        // En vez de un círculo, desvanecemos cada borde por separado con un
        // degradado lineal (arriba, abajo, izquierda, derecha); donde se
        // superponen (las esquinas) el desvanecido se nota un poco más
        // fuerte, que es justamente el efecto de viñeta esperado.
        val fadeColor = MaterialTheme.colorScheme.background.copy(alpha = settings.backgroundFadeOpacity)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val fadeWidth = size.width * 0.35f
                    val fadeHeight = size.height * 0.35f
                    onDrawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(0f to fadeColor, 1f to Color.Transparent),
                                startX = 0f,
                                endX = fadeWidth
                            )
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(0f to Color.Transparent, 1f to fadeColor),
                                startX = size.width - fadeWidth,
                                endX = size.width
                            )
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(0f to fadeColor, 1f to Color.Transparent),
                                startY = 0f,
                                endY = fadeHeight
                            )
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(0f to Color.Transparent, 1f to fadeColor),
                                startY = size.height - fadeHeight,
                                endY = size.height
                            )
                        )
                    }
                }
        )
    }
}
