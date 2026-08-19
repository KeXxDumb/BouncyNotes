package com.dumb.bouncynotes.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Cambiar el ícono de la app en Android se hace con "activity-alias": varios
// alias en el manifiesto apuntan a la misma MainActivity, cada uno con su
// propio ícono, y en runtime se habilita uno y se deshabilitan los demás con
// PackageManager. El lanzador (launcher) del teléfono lee ese estado para
// decidir qué ícono mostrar; algunos lanzadores tardan un momento o piden
// reabrir el cajón de apps para reflejar el cambio.
enum class AppIcon(val alias: String, val label: String) {
    DEFAULT("com.dumb.bouncynotes.DefaultIconAlias", "Por defecto"),
    NOTE_GIRL("com.dumb.bouncynotes.NoteGirlIconAlias", "Note Girl")
}

object AppIconManager {
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        val noteGirlState = pm.getComponentEnabledSetting(
            ComponentName(context, AppIcon.NOTE_GIRL.alias)
        )
        return if (noteGirlState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            AppIcon.NOTE_GIRL
        } else {
            AppIcon.DEFAULT
        }
    }

    fun setIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        try {
            // Importante: deshabilitar TODOS primero, y recién en un segundo paso
            // habilitar el elegido, en dos pasadas separadas. Si se hace en una
            // sola pasada (deshabilitar el resto y habilitar el elegido mezclado,
            // en el orden que sea) hay una ventana muy breve donde puede haber dos
            // alias habilitados a la vez (o ninguno), y varios lanzadores de
            // Android no manejan bien esa ambigüedad: se quedan con el ícono
            // anterior en caché y nunca lo actualizan hasta reinstalar la app.
            AppIcon.entries.forEach { candidate ->
                pm.setComponentEnabledSetting(
                    ComponentName(context, candidate.alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            pm.setComponentEnabledSetting(
                ComponentName(context, icon.alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
            // Si algo falla acá no vale la pena tirar abajo toda la pantalla de
            // Ajustes por esto; el usuario simplemente ve que el ícono no cambió.
        }
    }
}
