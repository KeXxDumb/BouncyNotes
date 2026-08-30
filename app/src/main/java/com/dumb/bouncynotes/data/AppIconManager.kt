package com.dumb.bouncynotes.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.dumb.bouncynotes.R
import kotlin.system.exitProcess

// Historial resumido (ver estado-actual.md para el detalle completo):
// v7: se agregó capa <monochrome> (adaptive-icon) tras comparar contra una
// app de referencia real. v8: renombrado por fruta. v9: DESCARTADO
// <adaptive-icon> por completo — tras confirmar que ni monochrome ni
// arreglar shrinkResources bastaban, se volvió al mecanismo plano de
// Android (@drawable normal, un solo PNG, sin background/foreground/
// monochrome) — mismo mecanismo que usaban todas las apps antes de
// Android 8, y el que finalmente funcionó en el dispositivo real. v10: se
// sacaron los 3 íconos de prueba de color (ya cumplieron su función) y
// Note Girl pasó a ser la ilustración remasterizada del artista.
enum class AppIcon(val alias: String, val label: String, val drawableResId: Int) {
    PEACH("com.dumb.bouncynotes.PeachIconAlias", "Durazno", R.drawable.ic_icon_peach),
    NOTE_GIRL("com.dumb.bouncynotes.NoteGirlIconAlias", "Note Girl", R.drawable.ic_icon_notegirl),
    MELONS("com.dumb.bouncynotes.MelonsIconAlias", "Melones", R.drawable.ic_icon_melons)
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
