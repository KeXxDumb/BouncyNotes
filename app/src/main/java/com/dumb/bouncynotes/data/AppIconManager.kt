package com.dumb.bouncynotes.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.dumb.bouncynotes.R
import kotlin.system.exitProcess

// v7 — algoritmo calcado de una app de referencia real (Goodwy Gallery), con
// capa <monochrome> agregada. Con eso, "Durazno" y "Melones" (vectoriales)
// refrescaban bien; "Note Girl" (PNG) no.
//
// v8 — íconos renombrados por la fruta que llevan.
//
// v9 — DESCARTADO EL SISTEMA ADAPTIVE-ICON POR COMPLETO. Después de
// encontrar y arreglar varios problemas reales (monochrome faltante,
// shrinkResources borrando PNGs referenciados solo desde un activity-alias)
// el ícono seguía sin cambiar bien en el dispositivo del usuario — así que
// en vez de seguir agregando parches sobre <adaptive-icon> (que trae capas,
// máscaras, caché de "íconos con temas" por launcher/OEM, y ya nos rompió
// de tres formas distintas), se vuelve al mecanismo más simple y más viejo
// de Android: un ícono plano (@drawable normal, un solo PNG, sin
// background/foreground/monochrome). Es el mismo mecanismo que usaban todas
// las apps antes de Android 8 y sigue siendo 100% válido — se pierde el
// recorte prolijo a la forma de cada launcher, pero se elimina toda la
// superficie de bugs que veníamos peleando.
enum class AppIcon(val alias: String, val label: String, val drawableResId: Int) {
    PEACH("com.dumb.bouncynotes.PeachIconAlias", "Durazno", R.drawable.ic_icon_peach),
    NOTE_GIRL("com.dumb.bouncynotes.NoteGirlIconAlias", "Note Girl", R.drawable.ic_icon_notegirl),
    MELONS("com.dumb.bouncynotes.MelonsIconAlias", "Melones", R.drawable.ic_icon_melons),

    // EXPERIMENTO TEMPORAL: misma imagen, 3 matices de color. Sacar estas 3
    // entradas una vez que se confirme que el ícono plano sí refresca bien.
    PNG_TEST_ORIGINAL("com.dumb.bouncynotes.PngTestOriginalIconAlias", "PNG orig.", R.drawable.ic_icon_pngtest_original),
    PNG_TEST_TEAL("com.dumb.bouncynotes.PngTestTealIconAlias", "PNG teal", R.drawable.ic_icon_pngtest_teal),
    PNG_TEST_VIOLET("com.dumb.bouncynotes.PngTestVioletIconAlias", "PNG violeta", R.drawable.ic_icon_pngtest_violet)
}

object AppIconManager {
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            pm.getComponentEnabledSetting(ComponentName(context, icon.alias)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.PEACH
    }

    /**
     * Estado real, tal cual lo devuelve PackageManager, de cada alias — para
     * mostrarlo en la UI de Ajustes como diagnóstico.
     */
    fun rawStates(context: Context): List<Pair<AppIcon, String>> {
        val pm = context.packageManager
        return AppIcon.entries.map { icon ->
            val state = pm.getComponentEnabledSetting(ComponentName(context, icon.alias))
            val label = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED"
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED"
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT"
                else -> "? ($state)"
            }
            icon to label
        }
    }

    /**
     * Algoritmo calcado de la referencia: apagar TODOS los alias primero,
     * después prender solo el elegido, siempre con DONT_KILL_APP.
     */
    fun setIcon(context: Context, icon: AppIcon): Result<Unit> {
        val pm = context.packageManager
        if (current(context) == icon) return Result.success(Unit)
        return try {
            AppIcon.entries.forEach { entry ->
                pm.setComponentEnabledSetting(
                    ComponentName(context, entry.alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            pm.setComponentEnabledSetting(
                ComponentName(context, icon.alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reinicio MANUAL y OPCIONAL: se mantiene por si hace falta como último
     * recurso. Nunca se dispara automáticamente.
     */
    fun restartProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
