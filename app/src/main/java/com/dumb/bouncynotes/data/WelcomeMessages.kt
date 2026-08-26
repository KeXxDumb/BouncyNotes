package com.dumb.bouncynotes.data

import java.util.Calendar

// Mensaje de bienvenida que se ve en el título de la barra superior al abrir
// la app, antes de pasar al título "regular" (ver TitleMode en
// SettingsRepository.kt). Se elige uno al azar entre los que apliquen a la
// hora actual del dispositivo.
object WelcomeMessages {

    // OJO: esto es una variable de proceso, NO se guarda en DataStore a
    // propósito. Tiene que resetearse cada vez que el proceso de la app
    // arranca de cero (abrir la app "de verdad"), pero NO cada vez que se
    // vuelve a la lista de notas desde otra pantalla (abrir una nota,
    // Ajustes, etc.) — si viviera en DataStore o en un ViewModel con scope
    // de Activity, volver de Ajustes a la lista mostraría de nuevo un
    // mensaje de bienvenida, que es justo lo que NO se quiere ("al cambiar
    // de enfoque muestran textos regulares").
    private var shownThisSession = false

    // Devuelve un mensaje de bienvenida la PRIMERA vez que se llama en todo
    // el proceso, y null todas las veces siguientes.
    fun consumeIfFirstOpenThisSession(): String? {
        if (shownThisSession) return null
        shownThisSession = true
        return randomMessage()
    }

    private class Entry(val text: String, val matchesHour: (Int) -> Boolean)

    private val entries = listOf(
        Entry("Buenos días ☀️") { it in 5..11 },
        Entry("Arriba, que hay notas por anotar") { it in 5..11 },
        Entry("Buenas tardes") { it in 12..18 },
        Entry("A mitad del día, a por más ideas") { it in 12..18 },
        Entry("Buenas noches 🌙") { it in 19..23 || it in 0..4 },
        Entry("Última ronda antes de dormir") { it in 19..23 || it in 0..4 },
        Entry("¡Bienvenido/a de nuevo! 🍑") { true },
        Entry("Qué bueno verte por acá") { true },
        Entry("Tus notas te estaban esperando") { true },
        Entry("Hora de organizar ideas") { true }
    )

    private fun randomMessage(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val matching = entries.filter { it.matchesHour(hour) }
        return matching.random().text
    }
}
