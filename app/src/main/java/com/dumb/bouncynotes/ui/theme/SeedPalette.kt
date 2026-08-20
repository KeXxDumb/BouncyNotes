package com.dumb.bouncynotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min

// La implementación anterior hacía `lightColorScheme(primary = seed)` /
// `darkColorScheme(primary = seed)`: eso solo reemplazaba el color "primary"
// y dejaba todos los demás roles (secondary, tertiary, containers, etc.) en
// los valores por defecto (violeta) del esquema base de Material 3. El
// resultado era una app que se veía a medias cambiada de color: el botón de
// "+" tomaba el nuevo color pero prácticamente todo lo demás seguía
// violeta, lo cual el usuario percibía como que "elegir color no hacía nada".
//
// Esta versión genera una paleta tonal completa a mano a partir del matiz
// (hue) del color semilla, en vez de depender de Material Color Utilities
// (no está entre las dependencias del proyecto): construye secondary y
// tertiary como matices análogos del primary, y deriva los "container"/"on"
// de cada rol ajustando luminosidad, igual que hace el sistema de color
// dinámico de Material You.
private data class Hsv(val h: Float, val s: Float, val v: Float)

private fun Color.toHsv(): Hsv {
    val argb = this.toArgb()
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return Hsv(hsv[0], hsv[1], hsv[2])
}

private fun hsv(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

private fun bestOnColor(bg: Color): Color {
    // Luminancia relativa aproximada para decidir si el texto/ícono encima
    // debe ser blanco o negro.
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (luminance > 0.55f) Color(0xFF1B1B1B) else Color.White
}

private class Tones(seed: Color) {
    private val base = seed.toHsv()
    val h = base.h
    val s = max(0.35f, min(base.s, 0.85f))

    fun tone(hueShift: Float, value: Float, saturation: Float = s): Color =
        hsv(h + hueShift, saturation, value)
}

fun buildSeedColorScheme(seedHex: String, dark: Boolean): ColorScheme {
    val seed = runCatching { Color(android.graphics.Color.parseColor(seedHex)) }
        .getOrDefault(Color(android.graphics.Color.parseColor(Seed)))
    val tones = Tones(seed)

    val primary = tones.tone(0f, if (dark) 0.85f else 0.55f)
    val secondary = tones.tone(-30f, if (dark) 0.55f else 0.65f, saturation = tones.s * 0.5f)
    val tertiary = tones.tone(60f, if (dark) 0.7f else 0.6f, saturation = tones.s * 0.7f)

    val primaryContainer = tones.tone(0f, if (dark) 0.32f else 0.88f, saturation = tones.s * 0.6f)
    val secondaryContainer = tones.tone(-30f, if (dark) 0.28f else 0.9f, saturation = tones.s * 0.35f)
    val tertiaryContainer = tones.tone(60f, if (dark) 0.3f else 0.87f, saturation = tones.s * 0.45f)

    val background = if (dark) tones.tone(0f, 0.09f, saturation = 0.08f) else tones.tone(0f, 0.99f, saturation = 0.03f)
    val surface = background
    val surfaceVariant = if (dark) tones.tone(0f, 0.18f, saturation = 0.1f) else tones.tone(0f, 0.93f, saturation = 0.08f)

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = bestOnColor(primaryContainer),
            secondary = secondary,
            onSecondary = bestOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = bestOnColor(secondaryContainer),
            tertiary = tertiary,
            onTertiary = bestOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = bestOnColor(tertiaryContainer),
            background = background,
            onBackground = bestOnColor(background),
            surface = surface,
            onSurface = bestOnColor(surface),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = bestOnColor(surfaceVariant)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = bestOnColor(primaryContainer),
            secondary = secondary,
            onSecondary = bestOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = bestOnColor(secondaryContainer),
            tertiary = tertiary,
            onTertiary = bestOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = bestOnColor(tertiaryContainer),
            background = background,
            onBackground = bestOnColor(background),
            surface = surface,
            onSurface = bestOnColor(surface),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = bestOnColor(surfaceVariant)
        )
    }
}
