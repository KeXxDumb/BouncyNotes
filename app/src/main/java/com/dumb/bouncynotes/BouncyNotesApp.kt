package com.dumb.bouncynotes

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

// Sin esto, AsyncImage (Coil) muestra un GIF como si fuera un PNG cualquiera:
// solo el primer cuadro, congelado, nunca anima. Coil necesita que se le
// registre explícitamente un decoder para GIF; ImageDecoderDecoder es el más
// eficiente pero solo existe desde Android 9 (API 28), así que en versiones
// más viejas se usa GifDecoder (más lento pero funciona en cualquier versión
// soportada por la app, que tiene minSdk 23).
class BouncyNotesApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
