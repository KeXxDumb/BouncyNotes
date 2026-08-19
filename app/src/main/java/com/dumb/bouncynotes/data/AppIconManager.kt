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
        AppIcon.entries.forEach { candidate ->
            val state = if (candidate == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context, candidate.alias),
                state,
                // DONT_KILL_APP: si no, Android mata el proceso de la app al
                // cambiar el estado de un componente, cerrando la app de golpe
                // justo después de tocar el botón en Ajustes.
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
