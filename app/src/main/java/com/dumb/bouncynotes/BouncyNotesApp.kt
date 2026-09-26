package com.dumb.bouncynotes

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.SettingsRepository
import com.dumb.bouncynotes.data.extractMediaRefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Sin esto, AsyncImage (Coil) muestra un GIF como si fuera un PNG cualquiera:
// solo el primer cuadro, congelado, nunca anima. Coil necesita que se le
// registre explícitamente un decoder para GIF; ImageDecoderDecoder es el más
// eficiente pero solo existe desde Android 9 (API 28), así que en versiones
// más viejas se usa GifDecoder (más lento pero funciona en cualquier versión
// soportada por la app, que tiene minSdk 23).
class BouncyNotesApp : Application(), ImageLoaderFactory {

    // Vive mientras viva el proceso (no atado a ninguna pantalla) — a
    // propósito, para la limpieza de huérfanos de más abajo: tiene que
    // sobrevivir aunque la Activity todavía no haya terminado de arrancar.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { cleanupOrphanMediaFiles() }
    }

    // Junta los nombres de archivo que SÍ están en uso (referenciados por
    // alguna nota, en cualquier estado — incluida la papelera, porque todavía
    // se puede restaurar — o por el pool de fondos de Ajustes) y le pide a
    // ImageStorage que borre lo que sobre. Ver el comentario grande en
    // ImageStorage.cleanupOrphans para el porqué de este barrido y de su
    // ventana de gracia de 24hs.
    private suspend fun cleanupOrphanMediaFiles() {
        try {
            val notes = NoteDatabase.getInstance(this).noteDao().getAll().first()
            val settings = SettingsRepository(this).settings.first()

            val referenced = mutableSetOf<String>()
            notes.forEach { note ->
                extractMediaRefs(note.content).forEach { referenced += it.fileName }
            }
            referenced += settings.backgroundImagePaths
            settings.backgroundImagePath?.let { referenced += it }

            ImageStorage.cleanupOrphans(this, referenced)
        } catch (e: Exception) {
            // Barrido best-effort: si algo falla acá (poco probable), no debe
            // tirar abajo el arranque de la app.
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // Optimización: sin esto Coil usa sus valores por defecto de
            // caché, que en la práctica ya andan bien, pero fijarlos acá
            // explícitamente evita depender de ese default (que podría
            // cambiar entre versiones de la librería) y deja margen para
            // ajustarlo a mano si en algún dispositivo hace falta. 25% de la
            // RAM disponible para la caché en memoria (miniaturas de la
            // lista, que se repiten mucho al scrollear) y un límite fijo de
            // disco para las imágenes ya decodificadas.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            // Antes cada AsyncImage aparecía de golpe apenas terminaba de
            // decodificar (un "pop" perceptible, sobre todo en las tarjetas
            // de la lista al scrollear rápido). Un crossfade corto lo
            // suaviza sin agregar demora notable a la carga.
            .crossfade(150)
            .build()
    }
}
