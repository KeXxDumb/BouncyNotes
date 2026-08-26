package com.dumb.bouncynotes.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.dumb.bouncynotes.R

// Reproduce, al azar, uno de los 3 sonidos al tocar el 🍑. SoundPool (no
// MediaPlayer) porque es la API pensada justo para esto: efectos cortos que
// se pueden disparar muchas veces seguidas (toques rápidos y repetidos)
// sin el delay de preparar un MediaPlayer nuevo en cada toque.
class PeachSoundPlayer(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // load() es asíncrono (devuelve un id ya, pero el sonido puede tardar un
    // instante en quedar listo la primera vez) — normal en SoundPool, no
    // hace falta esperar nada a mano: si se llama a play() antes de que
    // termine de cargar, simplemente no suena esa vez puntual, sin crashear.
    private val soundIds = listOf(
        soundPool.load(context, R.raw.slap1, 1),
        soundPool.load(context, R.raw.slap2, 1),
        soundPool.load(context, R.raw.slap3, 1)
    )

    fun playRandom() {
        val id = soundIds.random()
        soundPool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
