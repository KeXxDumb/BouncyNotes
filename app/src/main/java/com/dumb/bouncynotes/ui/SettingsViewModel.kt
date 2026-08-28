package com.dumb.bouncynotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.SettingsCache
import com.dumb.bouncynotes.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    // BUG encontrado originalmente: la semilla de este StateFlow era
    // AppSettings() (todo en sus valores por defecto: sin imagen de fondo,
    // tema del sistema, etc.), y esa semilla se entrega de forma SÍNCRONA
    // apenas algo empieza a coleccionar el Flow — antes de que la lectura
    // real desde DataStore (que es asíncrona, tarda al menos una corrutina)
    // termine. La UI se armaba primero con esos valores de fábrica y un
    // instante después "saltaba" a los valores reales guardados — el
    // parpadeo molesto (fondo negro/sin imagen un instante).
    //
    // Un intento anterior sembraba esto con null y hacía que MainActivity
    // no dibujara nada hasta tener el dato real. Eso evita el "salto" pero
    // no el parpadeo en sí: mientras settings es null, MainActivity igual
    // tiene que mostrar ALGO (una Surface neutra), y esa Surface usa
    // NotesTheme con sus valores por defecto (isSystemInDarkTheme() sin
    // saber todavía si el usuario realmente quiere tema oscuro) — con el
    // celular en modo oscuro del sistema, ese "algo neutro" ya se ve como
    // una pantalla oscura/negra, aunque sea por una fracción de segundo.
    //
    // La semilla ahora es SettingsCache.read(...): una copia SINCRÓNICA (vía
    // SharedPreferences, no DataStore) de los últimos valores reales
    // guardados la vez anterior. Como se lee synchrónicamente, ya está
    // disponible en la primerísima composición — no hace falta ni esperar
    // ni esconder nada: el primer frame ya usa el tema/colores/fondo reales.
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsCache.read(application)
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
