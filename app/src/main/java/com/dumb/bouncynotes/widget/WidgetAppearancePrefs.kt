package com.dumb.bouncynotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dumb.bouncynotes.data.ThemeMode

// Modo de fondo del widget, independiente de claro/oscuro/sistema:
// - SOLID: el fondo de siempre, totalmente opaco.
// - TRANSLUCENT ("transparente", pedido): el MISMO color de fondo del modo
//   elegido (claro/oscuro/sistema), pero con poca opacidad — se transparenta
//   el wallpaper de atrás sin perder la referencia de qué tema es.
// - INVISIBLE: sin fondo del todo (0% opacidad) — pedido como variante
//   extra, para quien quiera el widget "flotando" directo sobre el
//   wallpaper.
enum class WidgetBackgroundMode { SOLID, TRANSLUCENT, INVISIBLE }

// Apariencia (tema + fondo) por instancia de widget (appWidgetId) — a
// diferencia de la primera versión de esto, que era UN ajuste global para
// todos los widgets a la vez (en Ajustes de la app). Se pidió que cada
// widget tenga su propia configuración, accesible desde el propio widget
// (como ya pasaba con "qué nota mostrar" en Nota fijada), no desde Ajustes.
//
// SharedPreferences por el mismo motivo que PinnedNoteWidgetPrefs: lectura
// y escritura síncronas, sin ningún viaje de ida y vuelta asíncrono (evita
// la clase de carrera que ya se vio con SettingsCache/DataStore para el
// ajuste global anterior).
private const val PREFS_NAME = "widget_appearance_prefs"

object WidgetAppearancePrefs {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemeMode(context: Context, widgetId: Int): ThemeMode =
        runCatching {
            ThemeMode.valueOf(prefs(context).getString(themeKeyFor(widgetId), null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

    fun getBackgroundMode(context: Context, widgetId: Int): WidgetBackgroundMode =
        runCatching {
            WidgetBackgroundMode.valueOf(prefs(context).getString(bgKeyFor(widgetId), null) ?: WidgetBackgroundMode.SOLID.name)
        }.getOrDefault(WidgetBackgroundMode.SOLID)

    fun setAppearance(context: Context, widgetId: Int, themeMode: ThemeMode, backgroundMode: WidgetBackgroundMode) {
        prefs(context).edit()
            .putString(themeKeyFor(widgetId), themeMode.name)
            .putString(bgKeyFor(widgetId), backgroundMode.name)
            .apply()
    }

    // La llaman los onDeleted() de cada provider, para no dejar preferencias
    // huérfanas de widgets ya borrados acumulándose para siempre.
    fun removeWidget(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove(themeKeyFor(widgetId))
            .remove(bgKeyFor(widgetId))
            .apply()
    }

    private fun themeKeyFor(widgetId: Int) = "theme_$widgetId"
    private fun bgKeyFor(widgetId: Int) = "bg_$widgetId"
}

// Click DIRECTO (PendingIntent.getActivity(), no broadcast) para abrir la
// config activity de un widget desde un botón/ícono del propio widget —
// mismo criterio que ya se usa para abrir una nota de forma confiable (ver
// PinnedNoteWidgetProvider.openNotePendingIntent): un click directo no
// depende del mecanismo de listas ni de ningún receiver de por medio.
fun configureActivityPendingIntent(context: Context, widgetId: Int, activityClass: Class<*>): PendingIntent {
    val intent = Intent(context, activityClass).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        data = Uri.parse("bouncynotes://widget/configure/$widgetId")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    return PendingIntent.getActivity(
        context, widgetId, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
