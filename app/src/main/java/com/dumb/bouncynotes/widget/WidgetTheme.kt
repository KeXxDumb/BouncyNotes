package com.dumb.bouncynotes.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import com.dumb.bouncynotes.data.SettingsCache
import com.dumb.bouncynotes.data.ThemeMode

data class WidgetColors(
    val backgroundRes: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val divider: Int,
    val buttonBackgroundRes: Int,
    // Solo afecta el fondo (ver applyWidgetBackground más abajo) — texto,
    // divisores y botones se calculan igual, sin importar esto, para que
    // sigan siendo legibles sobre cualquier wallpaper.
    val transparentBackground: Boolean
)

private const val TEXT_PRIMARY_LIGHT = 0xFF1C1B1F.toInt()
private const val TEXT_PRIMARY_DARK = 0xFFE6E1E5.toInt()
private const val TEXT_SECONDARY_LIGHT = 0xFF49454F.toInt()
private const val TEXT_SECONDARY_DARK = 0xFFCAC4D0.toInt()
// Un widget no tiene forma confiable de leer el color de acento dinámico
// (semilla) que arma SeedPalette.kt para el resto de la app — no hay
// Compose ni MaterialTheme acá. Se usa un gris semitransparente fijo en su
// lugar, lo bastante opaco (30%) para que el divisor se note de verdad
// ("notorio", como se pidió) sin ser un color que pueda desentonar contra
// cualquier fondo/semilla que el usuario haya elegido.
private const val DIVIDER_LIGHT = 0x4D000000
private const val DIVIDER_DARK = 0x4DFFFFFF.toInt()

// Independiente del tema de la app en sí (settings.themeMode): un widget
// vive sobre el wallpaper del usuario, en la pantalla de inicio, así que
// puede convenir elegir un tema distinto ahí (ver Ajustes > Widgets). No
// se puede leer ?android:attr/colorBackground de un tema propio de forma
// confiable desde un widget — así que se resuelve a mano, leyendo el mismo
// caché sincrónico (SettingsCache) que ya existía para pintar el primer
// frame de la app sin parpadeo.
fun resolveWidgetColors(context: Context): WidgetColors {
    val settings = SettingsCache.read(context)
    val dark = when (settings.widgetThemeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> {
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            uiMode == Configuration.UI_MODE_NIGHT_YES
        }
    }
    return if (dark) {
        WidgetColors(
            backgroundRes = com.dumb.bouncynotes.R.drawable.widget_background_dark,
            textPrimary = TEXT_PRIMARY_DARK,
            textSecondary = TEXT_SECONDARY_DARK,
            divider = DIVIDER_DARK,
            buttonBackgroundRes = com.dumb.bouncynotes.R.drawable.widget_button_dark,
            transparentBackground = settings.widgetTransparentBackground
        )
    } else {
        WidgetColors(
            backgroundRes = com.dumb.bouncynotes.R.drawable.widget_background_light,
            textPrimary = TEXT_PRIMARY_LIGHT,
            textSecondary = TEXT_SECONDARY_LIGHT,
            divider = DIVIDER_LIGHT,
            buttonBackgroundRes = com.dumb.bouncynotes.R.drawable.widget_button_light,
            transparentBackground = settings.widgetTransparentBackground
        )
    }
}

// Un solo lugar para aplicar el fondo del contenedor raíz de un widget —
// así "transparente" queda resuelto una sola vez, en vez de repetir el
// if/else en cada uno de los 4 providers. Transparente = sin la tarjeta de
// fondo (ni clara ni oscura) para que se vea el wallpaper detrás; el resto
// de los colores (texto, divisor, botones) no cambian.
fun applyWidgetBackground(views: RemoteViews, layoutId: Int, colors: WidgetColors) {
    if (colors.transparentBackground) {
        views.setInt(layoutId, "setBackgroundColor", Color.TRANSPARENT)
    } else {
        views.setInt(layoutId, "setBackgroundResource", colors.backgroundRes)
    }
}
