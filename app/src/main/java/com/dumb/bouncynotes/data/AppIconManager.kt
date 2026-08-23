package com.dumb.bouncynotes.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

// Cambiar el ícono de la app en Android se hace con "activity-alias": varios
// alias en el manifiesto apuntan a la misma MainActivity, cada uno con su
// propio ícono, y en runtime se habilita uno y se deshabilitan los demás con
// PackageManager. El lanzador (launcher) del teléfono lee ese estado para
// decidir qué ícono mostrar.
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

    // HISTORIAL de este método, porque ya se rompió de dos formas distintas:
    //
    // v1: cambiaba el alias con PackageManager.DONT_KILL_APP y no tocaba el
    // proceso para nada. Diagnóstico en su momento (sin poder probar en un
    // dispositivo real): que el ícono quedaba cacheado en algunos launchers
    // (Samsung One UI, Xiaomi MIUI) mientras el proceso siguiera vivo.
    //
    // v2 (la anterior a esta): para "forzar" el refresco en esos launchers,
    // mataba el proceso con Process.killProcess() y programaba una alarma de
    // AlarmManager ~300ms después para relanzar MainActivity. Probado en un
    // dispositivo real, el resultado fue PEOR: el cambio de ícono dejó de
    // andar por completo. La causa es una restricción de Android bastante
    // más nueva que ese truco: desde Android 10, el sistema bloquea que un
    // PendingIntent.getActivity() abra una Activity si el proceso que lo
    // programó ya no tiene ninguna ventana/actividad visible en ese momento
    // ("restricciones de inicio de actividades en segundo plano"). Como acá
    // el proceso ya estaba MUERTO (por el killProcess de antes) cuando la
    // alarma disparaba, Android simplemente descartaba el intento de abrir
    // MainActivity sin ningún error visible: la app quedaba cerrada y no
    // volvía a abrirse sola. Es decir, el "arreglo" para el problema de
    // cacheo en Samsung/MIUI terminó rompiendo la función en TODOS los
    // teléfonos modernos.
    //
    // v3 (esta versión): volver al cambio de componente simple con
    // DONT_KILL_APP, sin matar ni relanzar nada — el mismo mecanismo que usa
    // Goodwy Messages (github.com/Goodwy/Messages, ver
    // toggleAppIconColor()/checkAppIconColor() en su librería Goodwy-Commons,
    // github.com/Goodwy/Goodwy-Commons), confirmado funcionando en un
    // dispositivo real. Si en algún launcher puntual el ícono tarda en
    // refrescarse mientras la app sigue abierta, es una limitación normal de
    // ESE launcher (decide cuándo releer los metadatos del paquete) y no algo
    // que la app pueda forzar sin volver a romper lo de arriba; basta con
    // cerrar y reabrir la app (no hace falta reiniciar el teléfono) para que
    // se vea actualizado.
    fun setIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        try {
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
        } catch (_: Exception) {
            // Si ni siquiera pudimos cambiar el estado del componente, no hay
            // nada más que hacer acá.
        }
    }
}
