package com.dumb.bouncynotes.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.widget.RemoteViews
import com.dumb.bouncynotes.data.ThemeMode

data class WidgetColors(
    val backgroundRes: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val divider: Int,
    val buttonBackgroundRes: Int,
    // Solo lo usa applyWidgetBackground para decidir CÓMO aplicar
    // backgroundRes (o si en cambio hay que dejarlo transparente del
    // todo) — texto/divisor/botones no dependen de esto, siguen
    // calculándose igual sea cual sea el modo de fondo, para que sigan
    // siendo legibles sobre cualquier wallpaper.
    val backgroundMode: WidgetBackgroundMode
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

// Apariencia de ESTE widget puntual (widgetId) — independiente del tema de
// la app en sí (settings.themeMode) y de cualquier otro widget: cada
// instancia se configura por separado (ver WidgetAppearancePrefs y las
// Activities de configuración de cada widget). No se puede leer
// ?android:attr/colorBackground de un tema propio de forma confiable desde
// un widget, así que se resuelve a mano.
fun resolveWidgetColors(context: Context, widgetId: Int): WidgetColors {
    val themeMode = WidgetAppearancePrefs.getThemeMode(context, widgetId)
    val backgroundMode = WidgetAppearancePrefs.getBackgroundMode(context, widgetId)
    val dark = when (themeMode) {
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
            backgroundMode = backgroundMode
        )
    } else {
        WidgetColors(
            backgroundRes = com.dumb.bouncynotes.R.drawable.widget_background_light,
            textPrimary = TEXT_PRIMARY_LIGHT,
            textSecondary = TEXT_SECONDARY_LIGHT,
            divider = DIVIDER_LIGHT,
            buttonBackgroundRes = com.dumb.bouncynotes.R.drawable.widget_button_light,
            backgroundMode = backgroundMode
        )
    }
}

// Un solo lugar para aplicar el fondo del contenedor raíz de un widget —
// así los 3 modos quedan resueltos una sola vez, en vez de repetir el
// if/else en cada uno de los 4 providers.
fun applyWidgetBackground(views: RemoteViews, layoutId: Int, colors: WidgetColors) {
    when (colors.backgroundMode) {
        WidgetBackgroundMode.SOLID -> views.setInt(layoutId, "setBackgroundResource", colors.backgroundRes)
        // Reusa el MISMO drawable que SOLID (respeta el tema elegido, con
        // sus esquinas redondeadas) pero dibujado con poca opacidad en vez
        // de intentar mezclar colores a mano acá — más simple y
        // predecible: ver widget_background_*_translucent.xml.
        WidgetBackgroundMode.TRANSLUCENT -> {
            val translucentRes = if (colors.backgroundRes == com.dumb.bouncynotes.R.drawable.widget_background_dark) {
                com.dumb.bouncynotes.R.drawable.widget_background_dark_translucent
            } else {
                com.dumb.bouncynotes.R.drawable.widget_background_light_translucent
            }
            views.setInt(layoutId, "setBackgroundResource", translucentRes)
        }
        WidgetBackgroundMode.INVISIBLE -> views.setInt(layoutId, "setBackgroundColor", Color.TRANSPARENT)
    }
}
