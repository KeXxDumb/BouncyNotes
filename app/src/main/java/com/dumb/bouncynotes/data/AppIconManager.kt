package com.dumb.bouncynotes.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import kotlin.system.exitProcess

// v7 — REESCRITO DESDE CERO como experimento controlado, después de
// confirmar con una app de referencia real (Goodwy Gallery, que usa este
// mismo mecanismo de base) que el cambio de ícono SÍ funciona instantáneo en
// el mismo dispositivo donde a BouncyNotes no se le reflejaba. Eso descartó
// la teoría de "es un límite del launcher/OS" — el problema tenía que estar
// del lado de esta app.
//
// Comparando el manifiesto real de esa app contra el nuestro, la diferencia
// concreta encontrada fue: NINGUNO de nuestros tres íconos tenía la capa
// <monochrome> (la usada por "íconos con temas" de Material You, Android
// 13+), mientras que TODOS los de la referencia sí la tienen. Sin esa capa,
// algunos launchers (Samsung One UI, Xiaomi HyperOS) generan y cachean la
// versión temática del ícono una sola vez POR PAQUETE instalado, y esa
// caché no se invalida al cambiar de alias en runtime — solo se refresca
// reinstalando la app. Eso explicaría por qué el diagnóstico de
// PackageManager (ver rawStates() más abajo) siempre mostraba el cambio
// como aceptado, pero la pantalla de inicio seguía sin reflejarlo.
//
// Este archivo reimplementa el cambio de ícono calcando el algoritmo real
// de la referencia (ver Context-styling.kt de Goodwy Commons,
// checkAppIconColor()/toggleAppIconColor()): deshabilitar TODOS los alias
// primero (DISABLED explícito, en un loop) y recién después habilitar el
// elegido (ENABLED), siempre con DONT_KILL_APP. Se abandona a propósito la
// distinción DEFAULT-vs-DISABLED de la v5/v6 (que en su momento se pensó
// necesaria) para que este sea un experimento limpio: si ahora SÍ funciona
// con el algoritmo simple + monochrome agregado, confirma que el problema
// real siempre fue la capa monochrome faltante, no el algoritmo de
// habilitar/deshabilitar.
enum class AppIcon(val alias: String, val label: String) {
    DEFAULT("com.dumb.bouncynotes.DefaultIconAlias", "Por defecto"),
    NOTE_GIRL("com.dumb.bouncynotes.NoteGirlIconAlias", "Note Girl"),
    // Reemplaza al viejo "Purple Note" (PNG con foreground vacío, sin
    // monochrome). Este es el opuesto: 100% vectorial, para comparar contra
    // Note Girl (PNG) ahora que ambos tienen su capa monochrome.
    VECTOR_TEST("com.dumb.bouncynotes.VectorTestIconAlias", "Prueba vectorial")
}

object AppIconManager {
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { icon ->
            pm.getComponentEnabledSetting(ComponentName(context, icon.alias)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.DEFAULT
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
     * después prender solo el elegido. Sin distinción DEFAULT/DISABLED a
     * propósito (ver comentario de arriba del archivo) — es la parte que
     * este experimento está poniendo a prueba.
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
     * Reinicio MANUAL y OPCIONAL: se mantiene igual que en la v6, por si
     * hace falta como último recurso incluso después del fix de monochrome.
     * Nunca se dispara automáticamente.
     */
    fun restartProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
