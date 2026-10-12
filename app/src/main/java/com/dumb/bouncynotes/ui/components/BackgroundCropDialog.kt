package com.dumb.bouncynotes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

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
@Composable
fun BackgroundCropDialog(
    sourceBitmap: Bitmap,
    frameAspectRatio: Float,
    onConfirm: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    var userScale by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }

    val srcW = sourceBitmap.width.toFloat()
    val srcH = sourceBitmap.height.toFloat()

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
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .aspectRatio(frameAspectRatio)
                        .clip(RoundedCornerShape(16.dp))
                        .onSizeChanged { frameSize = it }
                        .pointerInput(Unit) {
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
                    Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.None,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val base = baseFillScale(frameSize, srcW, srcH)
                                val eff = base * userScale
                                scaleX = eff
                                scaleY = eff
                                translationX = panX
                                translationY = panY
                            }
                    )
                }
            }

            Text(
                "Pellizcá para acercar, arrastrá para mover",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
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
