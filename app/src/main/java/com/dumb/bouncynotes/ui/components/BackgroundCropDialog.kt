package com.dumb.bouncynotes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.ImageStorage
import java.io.File
import kotlin.math.roundToInt

// Herramientas del riel inferior que abren un control propio (un deslizador
// o la tira de miniaturas) en el área fija de arriba del riel. Las otras
// opciones del riel son interruptores que actúan al tocarlas.
private enum class BgTool { IMAGES, IMAGE_OPACITY, FADE_OPACITY, TOP_BAR_OPACITY }

// Ajustar el recorte de la imagen de fondo ANTES de aplicarla — pedido
// explícito, porque antes el recorte lo elegía Compose solo
// (ContentScale.Crop: siempre centrado, sin forma de ajustarlo).
//
// Reusa el mismo gesto de pellizco/arrastre que ya existe en
// ImageViewerScreen.kt (detectTransformGestures + graphicsLayer) — ver ese
// archivo para el precedente. La diferencia clave con ese visor: acá el
// Image NO usa ContentScale.Crop de Compose para el encuadre inicial —
// usa ContentScale.None (tamaño nativo) y es ESTE archivo el que calcula a
// mano el escalado base para llenar el marco. Si se usara Crop de Compose
// para eso, Compose recortaría y DESCARTARÍA de entrada los bordes de una
// foto con una proporción muy distinta a la del marco — y una vez
// descartados, ya no hay forma de volver a acceder a esos bordes
// pellizcando/arrastrando después. Haciendo el escalado a mano, SIEMPRE
// está la imagen entera disponible para mover, sea cual sea su proporción
// original.
//
// Todo el cálculo (acá y en cropToBitmap) se mantiene en PÍXELES reales de
// pantalla (nunca dp) de punta a punta — mezclar las dos unidades es la
// forma más fácil de que el recorte final no coincida con lo que se vio en
// pantalla.
//
// REDISEÑO "editor de imagen": esta pantalla ahora también es donde se
// ajusta TODO el fondo. Debajo del texto de ayuda hay un riel horizontal
// deslizable con todas las opciones (imágenes del pool y agregar varias,
// monocromo, opacidades, desvanecido, barra, mostrar en notas). Los cambios
// de esas opciones se aplican EN VIVO sobre el marco (tinte, opacidad,
// desvanecido de bordes y una maqueta de barra/tarjetas) y se guardan al
// instante vía onUpdate; "Cancelar"/"Listo" solo afectan al recorte.
// Los efectos del marco son solo visuales: el cálculo del recorte
// (baseFillScale/cropToBitmap) no los toca.
@Composable
fun BackgroundCropDialog(
    sourceBitmap: Bitmap,
    frameAspectRatio: Float,
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    poolFileNames: List<String>,
    // Archivo del pool que se está reajustando (null si es una imagen nueva
    // todavía no guardada). No se puede quitar mientras se edita.
    currentFileName: String?,
    onSelectImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAddImages: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    // Atados a sourceBitmap: al cambiar de imagen desde las miniaturas, el
    // encuadre vuelve a empezar en vez de arrastrar el de la anterior.
    var userScale by remember(sourceBitmap) { mutableStateOf(1f) }
    var panX by remember(sourceBitmap) { mutableStateOf(0f) }
    var panY by remember(sourceBitmap) { mutableStateOf(0f) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedTool by remember { mutableStateOf(BgTool.IMAGES) }
    var showMock by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val srcW = sourceBitmap.width.toFloat()
    val srcH = sourceBitmap.height.toFloat()

    // Si estaba elegida "Op. bordes" y se apaga el desvanecido, esa
    // herramienta desaparece del riel: se vuelve a Imágenes.
    val tool = if (selectedTool == BgTool.FADE_OPACITY && !settings.backgroundFade) BgTool.IMAGES else selectedTool

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color.White)
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Ajustar recorte", color = Color.White)
                }
                TextButton(onClick = {
                    onConfirm(cropToBitmap(sourceBitmap, frameSize, userScale, panX, panY))
                }) {
                    Text("Listo", color = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(frameAspectRatio)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { frameSize = it }
                        .pointerInput(sourceBitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Mismo rango [1f, 4f] que el visor de
                                // imágenes (ImageViewerScreen) usa [1f, 5f]
                                // para el zoom de lectura — acá un poco
                                // menos porque esto es para elegir encuadre,
                                // no para ver detalle.
                                val newScale = (userScale * zoom).coerceIn(1f, 4f)
                                val base = baseFillScale(frameSize, srcW, srcH)
                                val eff = base * newScale
                                // El pan se acota en CADA gesto (no solo al
                                // confirmar) para que nunca se pueda arrastrar
                                // hasta ver un borde vacío fuera de la foto —
                                // así lo que se ve siempre es un recorte
                                // válido, de punta a punta.
                                val maxPanX = ((srcW * eff - frameSize.width) / 2f).coerceAtLeast(0f)
                                val maxPanY = ((srcH * eff - frameSize.height) / 2f).coerceAtLeast(0f)
                                panX = (panX + pan.x).coerceIn(-maxPanX, maxPanX)
                                panY = (panY + pan.y).coerceIn(-maxPanY, maxPanY)
                                userScale = newScale
                            }
                        }
                ) {
                    // Respaldo: negro plano en modo monocromático (igual que
                    // en la lista de notas), color del tema si no.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (settings.backgroundMonochrome) Color.Black
                                else MaterialTheme.colorScheme.background
                            )
                    )
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.None,
                        colorFilter = if (settings.backgroundMonochrome) {
                            ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.Color)
                        } else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val base = baseFillScale(frameSize, srcW, srcH)
                                val eff = base * userScale
                                scaleX = eff
                                scaleY = eff
                                translationX = panX
                                translationY = panY
                                // Opacidad por graphicsLayer (no por el alpha
                                // de Image) para que el tinte también se
                                // desvanezca — mismo criterio que la lista.
                                alpha = settings.backgroundImageOpacity
                            }
                    )
                    if (settings.backgroundFade) {
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
                    // Maqueta: barra superior con su opacidad real y un par
                    // de tarjetas, para juzgar cómo queda el fondo con
                    // contenido encima. No tiene manejo de toques, así que
                    // los gestos de recorte pasan igual.
                    if (showMock) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = settings.topBarOpacity))
                            )
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(2) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Pellizcá para acercar, arrastrá para mover",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                textAlign = TextAlign.Center
            )

            // Área fija (alto constante, para que el marco de arriba no
            // cambie de tamaño al cambiar de herramienta y descuadre el
            // recorte) con el control de la herramienta elegida.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                when (tool) {
                    BgTool.IMAGES -> LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(poolFileNames, key = { it }) { fileName ->
                            val isCurrent = fileName == currentFileName
                            Box(modifier = Modifier.size(56.dp)) {
                                AsyncImage(
                                    model = File(ImageStorage.imagesDir(context), fileName),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(
                                            if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                            else Modifier
                                        )
                                        .clickable(enabled = !isCurrent) { onSelectImage(fileName) }
                                )
                                // No se puede quitar la imagen que se está
                                // editando: su recorte pendiente la
                                // reemplazaría de nuevo al confirmar.
                                if (!isCurrent) {
                                    CompactIconButton(
                                        onClick = { onRemoveImage(fileName) },
                                        icon = Icons.Filled.Close,
                                        contentDescription = "Quitar imagen",
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        buttonSize = 22.dp,
                                        iconSize = 14.dp,
                                        tint = Color.White,
                                        containerColor = Color.Black.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                        item {
                            // El selector permite elegir VARIAS a la vez.
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable { onAddImages() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Agregar imágenes", tint = Color.White)
                            }
                        }
                    }
                    BgTool.IMAGE_OPACITY -> RailSlider(
                        label = "Opacidad de la imagen",
                        value = settings.backgroundImageOpacity,
                        onValueChange = { v -> onUpdate { it.copy(backgroundImageOpacity = v) } }
                    )
                    BgTool.FADE_OPACITY -> RailSlider(
                        label = "Opacidad del desvanecido",
                        value = settings.backgroundFadeOpacity,
                        onValueChange = { v -> onUpdate { it.copy(backgroundFadeOpacity = v) } }
                    )
                    BgTool.TOP_BAR_OPACITY -> RailSlider(
                        label = "Opacidad de la barra de título",
                        value = settings.topBarOpacity,
                        onValueChange = { v -> onUpdate { it.copy(topBarOpacity = v) } }
                    )
                }
            }

            // Riel horizontal deslizable con todas las opciones. Las
            // herramientas (con control propio) quedan resaltadas cuando
            // están elegidas; los interruptores, cuando están activados.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RailTile(Icons.Filled.Image, "Imágenes", tool == BgTool.IMAGES) { selectedTool = BgTool.IMAGES }
                RailTile(Icons.Filled.Palette, "Monocromo", settings.backgroundMonochrome) {
                    onUpdate { it.copy(backgroundMonochrome = !it.backgroundMonochrome) }
                }
                RailTile(Icons.Filled.Opacity, "Opacidad", tool == BgTool.IMAGE_OPACITY) { selectedTool = BgTool.IMAGE_OPACITY }
                RailTile(Icons.Filled.BlurOn, "Bordes", settings.backgroundFade) {
                    onUpdate { it.copy(backgroundFade = !it.backgroundFade) }
                }
                if (settings.backgroundFade) {
                    RailTile(Icons.Filled.Opacity, "Op. bordes", tool == BgTool.FADE_OPACITY) { selectedTool = BgTool.FADE_OPACITY }
                }
                RailTile(Icons.Filled.VerticalAlignTop, "Barra", tool == BgTool.TOP_BAR_OPACITY) { selectedTool = BgTool.TOP_BAR_OPACITY }
                RailTile(Icons.Filled.Description, "En notas", settings.showBackgroundInNotes) {
                    onUpdate { it.copy(showBackgroundInNotes = !it.showBackgroundInNotes) }
                }
                RailTile(Icons.Filled.Visibility, "Maqueta", showMock) { showMock = !showMock }
            }
        }
    }
}

// Ficha del riel: ícono + nombre corto. Resaltada (color primario del tema)
// cuando la herramienta está elegida o el interruptor está activado.
@Composable
private fun RailTile(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, maxLines = 1)
    }
}

// Deslizador 0..100% del área de control del riel: etiqueta arriba, y
// debajo el deslizador con el porcentaje a la derecha.
@Composable
private fun RailSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Color.White)
        }
    }
}

// Escala mínima para que la imagen, a tamaño nativo, cubra todo el marco
// (equivalente a mano de lo que haría ContentScale.Crop) — es el punto de
// partida (userScale = 1f) sobre el que se pellizca para acercar más.
private fun baseFillScale(frameSize: IntSize, srcW: Float, srcH: Float): Float {
    if (frameSize.width == 0 || frameSize.height == 0 || srcW <= 0f || srcH <= 0f) return 1f
    return maxOf(frameSize.width / srcW, frameSize.height / srcH)
}

// La inversa EXACTA de la transformación de dibujado de arriba (mismo
// baseFillScale, mismo centrado, mismo pan) — por construcción, lo que
// recorta esta función es pixel a pixel lo mismo que se vio encuadrado en
// el marco. Si algún día se toca el graphicsLayer de arriba, esta función
// tiene que actualizarse junto con él.
private fun cropToBitmap(source: Bitmap, frameSize: IntSize, userScale: Float, panX: Float, panY: Float): Bitmap {
    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()
    val eff = baseFillScale(frameSize, srcW, srcH) * userScale

    fun frameToSource(fx: Float, fy: Float): Pair<Float, Float> {
        val sx = (fx - frameSize.width / 2f - panX) / eff + srcW / 2f
        val sy = (fy - frameSize.height / 2f - panY) / eff + srcH / 2f
        return sx to sy
    }

    val (leftF, topF) = frameToSource(0f, 0f)
    val (rightF, bottomF) = frameToSource(frameSize.width.toFloat(), frameSize.height.toFloat())

    // Recorte final SIEMPRE dentro de los límites reales del bitmap de
    // origen — por más que la matemática de arriba esté bien, un
    // redondeo de punto flotante en el borde exacto puede pedir 1px de
    // más y tirar abajo Bitmap.createBitmap con una excepción; estos
    // coerceIn son la red de seguridad.
    val left = leftF.roundToInt().coerceIn(0, source.width - 1)
    val top = topF.roundToInt().coerceIn(0, source.height - 1)
    val right = rightF.roundToInt().coerceIn(left + 1, source.width)
    val bottom = bottomF.roundToInt().coerceIn(top + 1, source.height)

    return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
}
