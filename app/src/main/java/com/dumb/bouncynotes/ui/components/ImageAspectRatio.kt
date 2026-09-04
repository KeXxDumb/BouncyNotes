package com.dumb.bouncynotes.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dumb.bouncynotes.data.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Relación de aspecto (ancho/alto) por nombre de archivo, cacheada en
// memoria para toda la sesión: leer las dimensiones de un archivo es rápido
// (ver ImageStorage.readImageDimensions), pero no hace falta repetirlo cada
// vez que la misma imagen vuelve a entrar en composición al scrollear.
private val aspectRatioCache = mutableMapOf<String, Float>()

private const val DEFAULT_IMAGE_ASPECT_RATIO = 4f / 3f

// Relación de aspecto REAL de una imagen ya guardada, para poder reservarle
// su alto correcto en una LazyColumn ANTES de que la imagen termine de
// cargar del todo (evita el salto de layout que hacía que el scroll rápido
// se sintiera trabado) — sin necesidad de recortarla a una proporción fija
// arbitraria, ya que acá se usa la proporción real de cada imagen. Mientras
// se determina la real (la primera vez que se ve esa imagen puntual en la
// sesión), se usa un fallback razonable — la lectura es solo el encabezado
// del archivo, así que esa ventana suele ser demasiado breve para notarse,
// y no vuelve a pasar para esa misma imagen en lo que dure la sesión.
@Composable
fun rememberImageAspectRatio(context: Context, fileName: String): Float {
    var ratio by remember(fileName) {
        mutableStateOf(aspectRatioCache[fileName] ?: DEFAULT_IMAGE_ASPECT_RATIO)
    }
    LaunchedEffect(fileName) {
        val cached = aspectRatioCache[fileName]
        if (cached != null) {
            ratio = cached
            return@LaunchedEffect
        }
        val dimensions = withContext(Dispatchers.IO) { ImageStorage.readImageDimensions(context, fileName) }
        if (dimensions != null) {
            val (width, height) = dimensions
            val computed = width.toFloat() / height.toFloat()
            aspectRatioCache[fileName] = computed
            ratio = computed
        }
    }
    return ratio
}
