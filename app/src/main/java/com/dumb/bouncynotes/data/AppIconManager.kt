package com.dumb.bouncynotes.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import kotlin.system.exitProcess

// Cambiar el ícono de la app en Android se hace con "activity-alias": varios
// alias en el manifiesto apuntan a la misma MainActivity, cada uno con su
// propio ícono, y en runtime se habilita uno y se deshabilitan los demás con
// PackageManager. El lanzador (launcher) del teléfono lee ese estado para
// decidir qué ícono mostrar.
enum class AppIcon(val alias: String, val label: String) {
    DEFAULT("com.dumb.bouncynotes.DefaultIconAlias", "Por defecto"),
    NOTE_GIRL("com.dumb.bouncynotes.NoteGirlIconAlias", "Note Girl"),
    // Ícono de prueba: ver el comentario en el activity-alias
    // PurpleNoteIconAlias del manifest y en
    // mipmap-anydpi-v26/ic_launcher_purple_note.xml — sirve para aislar si
    // el problema de refresco de ícono en MIUI depende de que el ícono sea
    // un PNG (a diferencia de DEFAULT, que es un color plano sin ningún
    // archivo de imagen).
    PURPLE_NOTE("com.dumb.bouncynotes.PurpleNoteIconAlias", "Morado (prueba PNG)")
}

object AppIconManager {
    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        // Antes esto solo miraba si NOTE_GIRL estaba habilitado y, si no,
        // asumía DEFAULT directo — con un tercer ícono eso hubiera
        // reportado mal el morado como si fuera el default. Ahora se busca
        // cuál de los tres alias está realmente habilitado.
        return AppIcon.entries.firstOrNull { icon ->
            pm.getComponentEnabledSetting(ComponentName(context, icon.alias)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } ?: AppIcon.DEFAULT
    }

    // HISTORIAL de este método, porque ya se rompió de varias formas
    // distintas — dejarlo documentado para no volver a probar lo mismo:
    //
    // v1: cambiaba el alias con PackageManager.DONT_KILL_APP y no tocaba el
    // proceso para nada. Diagnóstico en su momento (sin poder probar en un
    // dispositivo real): el ícono quedaba cacheado en algunos launchers
    // (Samsung One UI, Xiaomi MIUI) mientras el proceso siguiera vivo.
    //
    // v2: para "forzar" el refresco, mataba el proceso con
    // Process.killProcess() y programaba una alarma de AlarmManager ~300ms
    // después para relanzar MainActivity con un PendingIntent. Se rompió en
    // Android 10+: el sistema bloquea que un PendingIntent.getActivity()
    // abra una Activity si, para cuando la alarma dispara, el proceso que la
    // programó ya no tiene ninguna ventana visible ("restricciones de inicio
    // de actividades en segundo plano"). La app quedaba cerrada y no volvía
    // a abrirse sola.
    //
    // v3/v4: volvieron al cambio de alias simple (v3 sin matar nada, v4
    // matando el proceso después sin relanzarlo). Probadas en varios
    // dispositivos reales, el ícono NUNCA se actualizaba — ni con el
    // proceso vivo (v3) ni matándolo después (v4). La razón, encontrada
    // investigando cómo lo resuelven apps como Telegram (que usa este mismo
    // mecanismo de activity-alias, ver su AndroidManifest.xml en GitHub):
    // el bug real no era CUÁNDO matar el proceso, sino CÓMO se apagaban los
    // alias. v3/v4 deshabilitaban los TRES alias con
    // COMPONENT_ENABLED_STATE_DISABLED explícito, EN UN LOOP, y recién
    // después habilitaban el elegido. Android trata un DISABLED explícito
    // como un cambio real del "contrato" del paquete y puede matar el
    // proceso en cualquier punto de esa operación pase lo que pase
    // (DONT_KILL_APP se ignora en ese caso) — si el proceso moría a mitad
    // del loop, la app podía quedar sin NINGÚN alias habilitado.
    //
    // v5 (esta versión): dos cambios.
    //  1. Se habilita el elegido PRIMERO (antes que nada se toque otro
    //     alias): si Android mata el proceso en algún punto de esta
    //     operación, que sea DESPUÉS de que el ícono elegido ya haya
    //     quedado activo, nunca antes.
    //  2. Los demás alias ya NO se deshabilitan a mano con DISABLED: se
    //     resetean a COMPONENT_ENABLED_STATE_DEFAULT (volver al valor
    //     declarado en el manifiesto), que Android trata como un cambio
    //     mucho más liviano y en la práctica no dispara el mismo
    //     force-kill. La única excepción es DEFAULT: su valor "de fábrica"
    //     en el manifiesto es enabled=true, así que resetearlo a DEFAULT lo
    //     dejaría prendido de nuevo (dos íconos habilitados a la vez); para
    //     ese caso puntual (dejar el ícono "Por defecto" por otro) no queda
    //     otra que un DISABLED explícito, y ahí sí Android podría llegar a
    //     matar el proceso — por eso queda un reinicio manual opcional más
    //     abajo, ya no uno forzado automáticamente como en v4.
    fun setIcon(context: Context, icon: AppIcon): Boolean {
        val pm = context.packageManager
        return try {
            pm.setComponentEnabledSetting(
                ComponentName(context, icon.alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            AppIcon.entries.forEach { other ->
                if (other != icon) {
                    val state = if (other == AppIcon.DEFAULT) {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    }
                    pm.setComponentEnabledSetting(
                        ComponentName(context, other.alias),
                        state,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reinicio MANUAL y OPCIONAL: el usuario lo dispara a mano desde
     * Ajustes si, después de cambiar el ícono, su launcher (algunos, como
     * Samsung One UI o MIUI, cachean el ícono mientras el proceso de la app
     * sigue vivo) no lo refleja solo. A diferencia de v2/v4, esto nunca se
     * dispara automáticamente ni intenta relanzar la app por su cuenta —
     * mata el proceso y listo; reabrirla queda en manos del usuario,
     * tocando su ícono como de costumbre.
     */
    fun restartProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
