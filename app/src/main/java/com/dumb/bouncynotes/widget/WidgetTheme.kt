package com.dumb.bouncynotes.widget

import android.content.Context
import android.content.res.Configuration
import com.dumb.bouncynotes.data.SettingsCache
import com.dumb.bouncynotes.data.ThemeMode

data class WidgetColors(val backgroundRes: Int, val textPrimary: Int, val textSecondary: Int)

private const val TEXT_PRIMARY_LIGHT = 0xFF1C1B1F.toInt()
private const val TEXT_PRIMARY_DARK = 0xFFE6E1E5.toInt()
private const val TEXT_SECONDARY_LIGHT = 0xFF49454F.toInt()
private const val TEXT_SECONDARY_DARK = 0xFFCAC4D0.toInt()

// Un widget no puede leer ?android:attr/colorBackground de un tema propio de
// forma confiable (y menos aún el AppSettings.themeMode elegido DENTRO de la
// app, que puede diferir del modo claro/oscuro del sistema) — así que se
// resuelve a mano, con el mismo criterio que ya usa MainActivity para
// AppSettings.themeMode, leyendo el mismo caché sincrónico (SettingsCache)
// que ya existía para pintar el primer frame de la app sin parpadeo.
fun resolveWidgetColors(context: Context): WidgetColors {
    val settings = SettingsCache.read(context)
    val dark = when (settings.themeMode) {
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
            textSecondary = TEXT_SECONDARY_DARK
        )
    } else {
        WidgetColors(
            backgroundRes = com.dumb.bouncynotes.R.drawable.widget_background_light,
            textPrimary = TEXT_PRIMARY_LIGHT,
            textSecondary = TEXT_SECONDARY_LIGHT
        )
    }
}
