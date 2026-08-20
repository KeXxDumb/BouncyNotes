package com.dumb.bouncynotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColorHex: String = Seed,
    content: @Composable () -> Unit
) {
    // Antes esto hacía lightColorScheme(primary = seed) / darkColorScheme(primary =
    // seed): solo cambiaba el color "primary" y dejaba todo lo demás (secundario,
    // terciario, containers...) en el violeta por defecto de Material 3, así que
    // elegir un color de tema en Ajustes casi no se notaba. Ahora se genera una
    // paleta tonal completa a partir del matiz del color elegido (ver SeedPalette.kt).
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> buildSeedColorScheme(seedColorHex, darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
