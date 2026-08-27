package com.dumb.bouncynotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    // BUG encontrado: antes la semilla de este StateFlow era AppSettings()
    // (todo en sus valores por defecto: sin imagen de fondo, tema del
    // sistema, etc.), y esa semilla se entrega de forma SÍNCRONA apenas algo
    // empieza a coleccionar el Flow — antes de que la lectura real desde
    // DataStore (que es asíncrona, tarda unos milisegundos) termine. La UI
    // se armaba primero con esos valores por defecto (fondo negro/sin
    // imagen) y un instante después "saltaba" a los valores reales
    // guardados — el parpadeo molesto que se describe. Con la semilla en
    // null, MainActivity puede distinguir "todavía no se sabe" de "ya se
    // sabe y son estos los valores", y no dibuja nada hasta tener el dato
    // real.
    val settings: StateFlow<AppSettings?> = repository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
