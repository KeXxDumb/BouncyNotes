package com.dumb.bouncynotes.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import com.dumb.bouncynotes.MainActivity

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

    // OJO: la primera implementación cambiaba el estado de los alias con
    // PackageManager.DONT_KILL_APP (a propósito, para no cerrar la app) y ahí
    // quedaba: en el emulador/algunos Pixel se veía andar, pero en launchers
    // como los de Samsung (One UI), Xiaomi (MIUI) y varios otros, el ícono
    // mostrado queda cacheado mientras el proceso de la app sigue vivo, y
    // recién se refresca la próxima vez que el proceso arranca de cero. El
    // cambio en PackageManager era correcto (currentComponentEnabledSetting
    // ya reportaba el nuevo valor), pero visualmente "no hacía nada" en esos
    // teléfonos porque el launcher nunca se enteraba.
    //
    // Esta versión, en cambio, mata y reinicia el proceso de la app justo
    // después de cambiar el alias: eso fuerza a que TODOS los launchers
    // vuelvan a leer los metadatos del paquete (íconos incluidos) al
    // relanzarla, en vez de depender de que cada launcher decida refrescar su
    // caché por su cuenta.
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
            // Si ni siquiera pudimos cambiar el estado del componente, no
            // tiene sentido reiniciar la app para nada: mejor dejarla como
            // estaba y que el usuario simplemente vea que no cambió.
            return
        }

        restartToApplyIcon(context)
    }

    // Reinicia la app un instante después de matarla, usando una alarma casi
    // inmediata: es la forma estándar de "reiniciar la app" en Android (no
    // existe una API directa para eso). Sin esto, el cambio de ícono queda
    // aplicado a nivel de PackageManager pero invisible en varios launchers
    // hasta que el usuario cierre la app manualmente por su cuenta.
    private fun restartToApplyIcon(context: Context) {
        val restartIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 300, pendingIntent)
        Process.killProcess(Process.myPid())
    }
}
