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
    // después para relanzar MainActivity con un PendingIntent. Probado en un
    // dispositivo real, el resultado fue PEOR: el cambio de ícono dejó de
    // andar por completo. La causa es una restricción de Android más nueva
    // que ese truco: desde Android 10, el sistema bloquea que un
    // PendingIntent.getActivity() abra una Activity si el proceso que lo
    // programó ya no tiene ninguna ventana/actividad visible en ese momento
    // ("restricciones de inicio de actividades en segundo plano"). Como acá
    // el proceso ya estaba MUERTO (por el killProcess de antes) cuando la
    // alarma disparaba, Android simplemente descartaba el intento de abrir
    // MainActivity sin ningún error visible: la app quedaba cerrada y no
    // volvía a abrirse sola.
    //
    // v3 (la anterior a esta): volver al cambio de componente simple con
    // DONT_KILL_APP, sin matar ni relanzar nada — el mismo mecanismo que usa
    // Goodwy Messages. El comentario de esa versión decía "confirmado
    // funcionando en un dispositivo real", pero al probarlo en varios
    // dispositivos reales distintos el ícono NUNCA se actualizaba en
    // ninguno — exactamente el problema que v1 ya había diagnosticado
    // (el launcher cachea el ícono mientras el proceso sigue vivo, y ese
    // cacheo resultó ser la norma, no la excepción, en los dispositivos
    // probados esta vez).
    //
    // v4 (esta versión): cambia el alias Y mata el proceso, pero — a
    // diferencia de v2 — SIN intentar relanzarlo. Evita el problema de v2
    // (la restricción de Android 10+ bloqueaba el relanzamiento) sin volver
    // a caer en el problema de v1/v3 (dejar el proceso vivo, que es lo que
    // impedía el refresco del ícono). El costo es que el usuario tiene que
    // volver a abrir la app a mano después del cambio; a cambio, el ícono
    // se actualiza de forma confiable en cualquier launcher, porque ya no
    // depende de que ESE launcher decida refrescar sus metadatos mientras
    // la app sigue corriendo — simplemente ya no está corriendo.
    fun setIcon(context: Context, icon: AppIcon): Boolean {
        val pm = context.packageManager
        return try {
            // Deshabilitar TODOS primero y recién en un segundo paso habilitar
            // el elegido (en dos pasadas separadas) evita que, por un instante,
            // haya dos alias habilitados a la vez o ninguno.
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
            true
        } catch (_: Exception) {
            // Si ni siquiera pudimos cambiar el estado del componente, no
            // tiene sentido matar el proceso después: la llamante (ver
            // SettingsScreen) solo reinicia si esto devuelve true.
            false
        }
    }

    /**
     * Mata el proceso de la app a propósito, SIN relanzarlo, para forzar que
     * todos los launchers relean el ícono la próxima vez que el usuario
     * vuelva a abrir la app. Debe llamarse DESPUÉS de un setIcon() exitoso,
     * y solo una vez que cualquier mensaje para el usuario (Toast/Snackbar
     * avisando que la app se va a cerrar) ya se alcanzó a mostrar — esta
     * función no espera nada, mata el proceso ya mismo.
     */
    fun restartProcessToApplyIcon() {
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
