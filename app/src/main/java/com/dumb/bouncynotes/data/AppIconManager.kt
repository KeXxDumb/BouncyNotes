package com.dumb.bouncynotes.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.dumb.bouncynotes.R
import kotlin.system.exitProcess

// v7 — algoritmo calcado de una app de referencia real (Goodwy Gallery, que
// usa este mismo mecanismo de base) después de confirmar en el mismo
// dispositivo que el cambio de ícono SÍ funciona instantáneo ahí. La
// diferencia real encontrada: agregar la capa <monochrome> (íconos con
// temas, Android 13+) a los tres íconos. Con eso, "Durazno" y "Melones"
// (ambos 100% vectoriales) ya refrescan bien; "Note Girl" (el único basado
// en PNG) sigue sin reflejarse — evidencia de que el problema real es
// específicamente PNG vs vector en el launcher del usuario, no el algoritmo
// de habilitar/deshabilitar en sí.
//
// v8 — íconos renombrados por la fruta que llevan: DEFAULT -> PEACH,
// VECTOR_TEST -> MELONS. También se agrega mipmapResId a cada entrada para
// que la UI de Ajustes pueda mostrar el ícono REAL (el mismo recurso
// @mipmap que usa el sistema) en vez de una aproximación dibujada aparte.
enum class AppIcon(val alias: String, val label: String, val mipmapResId: Int) {
    PEACH("com.dumb.bouncynotes.PeachIconAlias", "Durazno", R.mipmap.ic_launcher),
    NOTE_GIRL("com.dumb.bouncynotes.NoteGirlIconAlias", "Note Girl", R.mipmap.ic_launcher_notegirl),
    MELONS("com.dumb.bouncynotes.MelonsIconAlias", "Melones", R.mipmap.ic_launcher_melons),

    // EXPERIMENTO TEMPORAL (ver comentario en el manifiesto): misma imagen,
    // 3 matices de color distintos, cada uno con su propio monochrome. Sacar
    // estas 3 entradas una vez que se confirme o descarte "PNG en general
    // no refresca en este launcher".
    PNG_TEST_ORIGINAL("com.dumb.bouncynotes.PngTestOriginalIconAlias", "PNG orig.", R.mipmap.ic_launcher_pngtest_original),
    PNG_TEST_TEAL("com.dumb.bouncynotes.PngTestTealIconAlias", "PNG teal", R.mipmap.ic_launcher_pngtest_teal),
    PNG_TEST_VIOLET("com.dumb.bouncynotes.PngTestVioletIconAlias", "PNG violeta", R.mipmap.ic_launcher_pngtest_violet)
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
