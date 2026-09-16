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
// opacityReduction resta puntos a settings.backgroundImageOpacity SOLO para
// esta pantalla (la lista sigue mostrando la opacidad configurada tal cual).
// BUG encontrado en la primera versión de esto: en vez de restar, se
// superponía una capa NEGRA aparte con alpha = opacidad configurada + 0.15
// — con la opacidad en 75%, esa capa quedaba al 90% de negro sólido ENCIMA
// de la imagen ya dibujada, así que el resultado visible terminaba siendo
// altísimamente más oscuro que "un poco" (se veía como si la opacidad
// estuviera en 15-25%, no en 75%): dos capas de opacidad no suman, se
// MULTIPLICAN (visibilidad final ≈ opacidad × (1 − capa_negra)), así que
// sumar 15 puntos a la capa negra en vez de a la opacidad de la imagen
// aplastaba el resultado mucho más de lo esperado, y encima el efecto crecía
// cuanto más alta ya estuviera la opacidad configurada. Restarle unos pocos
// puntos directo a la MISMA opacidad de la imagen (una sola capa, un solo
// alpha, sin componer dos) es lineal y predecible: 75% en la lista se ve
// como 60% en la nota, no como 15%.
@Composable
fun NoteBackgroundImage(
    settings: AppSettings,
    context: Context,
    opacityReduction: Float = 0f
) {
    val path = settings.backgroundImagePath ?: return
    // Respaldo opaco SIEMPRE presente (antes solo existía para el modo
    // monocromático) mientras Coil todavía no terminó de decodificar el
    // archivo de disco: sin esto, durante ese instante se ve lo que sea que
    // haya debajo del Scaffold (que acá es transparente a propósito, ver
    // NoteListScreen/NoteEditScreen) — un "flash" de la pantalla sin fondo
    // apenas se abre la app, ya que Coil no pinta nada en el primer frame,
    // recién cuando termina de leer y decodificar el bitmap.
    Box(
        modifier = Modifier.fillMaxSize().background(
            if (settings.backgroundMonochrome) Color.Black else MaterialTheme.colorScheme.background
        )
    )
    val effectiveOpacity = (settings.backgroundImageOpacity - opacityReduction).coerceIn(0f, 1f)
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
            .graphicsLayer(alpha = effectiveOpacity)
    )
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
