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

    /**
     * Estado real, tal cual lo devuelve PackageManager, de cada alias — para
     * mostrarlo en la UI de Ajustes como diagnóstico. Si esto no refleja el
     * cambio esperado después de tocar un ícono, el problema está en
     * PackageManager/el sistema (algo que la app no puede forzar); si SÍ lo
     * refleja pero el ícono de la pantalla de inicio no cambia, el problema
     * es 100% del launcher cacheando, no de esta app.
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
    // dispositivos reales, el ícono NUNCA se actualizaba.
    //
    // v5: se investigó por qué (comparando con Telegram, que usa este mismo
    // mecanismo) y se corrigió CÓMO se apagaban los alias: en vez de
    // deshabilitar los tres con DISABLED explícito en un loop (lo que v3/v4
    // hacían, y que Android puede tratar como un cambio de "contrato" real
    // del paquete que mata el proceso pase lo que pase), se resetea a
    // COMPONENT_ENABLED_STATE_DEFAULT el que no sea DEFAULT (volver al valor
    // declarado en el manifiesto), y solo se usa DISABLED explícito para
    // DEFAULT (su valor de fábrica es enabled=true, así que resetearlo a
    // DEFAULT lo dejaría prendido de nuevo). Igual, probado por el usuario
    // en varios dispositivos y con un launcher de terceros que sabía que sí
    // soporta cambio de ícono en otras apps: TAMPOCO se reflejó nada, en
    // ningún lado. Esa señal es fuerte — descarta que sea únicamente un
    // problema de "este launcher puntual cachea el ícono".
    //
    // v6 (esta versión): se buscó una referencia real, confirmada andando
    // (con una app de ejemplo pública, ver
    // github.com/anandankur2816/SwitchMyIconAndroid) que usa el mismo
    // mecanismo y el mismo criterio DISABLED-vs-DEFAULT de la v5. Dos
    // cambios respecto a v5:
    //  1. Orden: esa referencia apaga el alias VIEJO primero y recién
    //     después habilita el NUEVO (v5 lo hacía al revés). Se iguala el
    //     orden exacto por las dudas de que importe en algún dispositivo,
    //     ya que es el único punto real de esta lógica que hay confirmado
    //     funcionando en un caso real.
    //  2. Ya NO se traga la excepción en silencio (v1-v5 hacían
    //     catch(Exception){ false }, así que si algo fallaba de verdad —
    //     SecurityException, IllegalArgumentException por un ComponentName
    //     mal armado, etc. — nunca nos hubiéramos enterado). Ahora se
    //     devuelve el error real para poder mostrarlo en la UI (ver
    //     SettingsScreen) y de una vez por todas saber si el problema es
    //     "PackageManager rechaza la operación" o "la acepta pero el
    //     launcher no la refleja".
    fun setIcon(context: Context, icon: AppIcon): Result<Unit> {
        val pm = context.packageManager
        val previous = current(context)
        if (previous == icon) return Result.success(Unit)
        return try {
            // 1) Apagar el que estaba activo.
            if (previous == AppIcon.DEFAULT) {
                pm.setComponentEnabledSetting(
                    ComponentName(context, previous.alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } else {
                pm.setComponentEnabledSetting(
                    ComponentName(context, previous.alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }
            // 2) Prender el elegido.
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
     * Reinicio MANUAL y OPCIONAL: el usuario lo dispara a mano desde
     * Ajustes si, después de cambiar el ícono, su launcher (algunos, como
     * Samsung One UI o MIUI, cachean el ícono mientras el proceso de la app
     * sigue vivo) no lo refleja solo. Nunca se dispara automáticamente ni
     * intenta relanzar la app por su cuenta — mata el proceso y listo;
     * reabrirla queda en manos del usuario, tocando su ícono como de
     * costumbre.
     */
    fun restartProcess() {
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
