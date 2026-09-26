package com.dumb.bouncynotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object ImageStorage {

    // Límite de tamaño para videos insertados en una nota. Los videos no se
    // comprimen (re-codificar video en el teléfono es lento y complejo), así
    // que la única defensa contra que una nota pese cientos de MB es
    // rechazar archivos que ya vienen pesados desde el origen.
    const val MAX_VIDEO_BYTES = 25L * 1024 * 1024 // 25 MB

    fun imagesDir(context: Context): File {
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun newFileName(extension: String = "jpg"): String = "img_${UUID.randomUUID()}.$extension"

    private fun isGif(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.equals("image/gif", ignoreCase = true) == true

    // Copia el archivo tal cual, sin recomprimir (mejor calidad para referencias de arte).
    fun copyFromUri(context: Context, uri: Uri): String? {
        return try {
            // Si es GIF, hay que preservar la extensión .gif (para que Coil lo
            // reconozca y anime) y NUNCA pasar por BitmapFactory/compress más
            // abajo, que solo entiende de un cuadro fijo y mataría la animación.
            val fileName = if (isGif(context, uri)) newFileName("gif") else newFileName("jpg")
            val outFile = File(imagesDir(context), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            fileName
        } catch (e: Exception) {
            null
        }
    }

    // Decodifica y recomprime a la calidad indicada (ahorra espacio).
    fun compressFromUri(context: Context, uri: Uri, quality: Int): String? {
        // Los GIF nunca se recomprimen: BitmapFactory solo decodifica el primer
        // cuadro, así que "comprimir" un GIF lo convertiría en una imagen
        // estática sin querer. Se copian tal cual, igual que copyFromUri.
        if (isGif(context, uri)) return copyFromUri(context, uri)
        return try {
            val fileName = newFileName("jpg")
            val outFile = File(imagesDir(context), fileName)
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                outFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
                bitmap.recycle()
                fileName
            } else {
                copyFromUri(context, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun compressInPlace(context: Context, fileName: String, quality: Int) {
        // No re-comprimir gifs en el lugar tampoco, por la misma razón de arriba.
        if (fileName.endsWith(".gif", ignoreCase = true)) return
        try {
            val file = File(imagesDir(context), fileName)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out) }
            bitmap.recycle()
        } catch (e: Exception) {
        }
    }

    fun createCaptureFile(context: Context): Pair<File, Uri> {
        val fileName = newFileName("jpg")
        val file = File(imagesDir(context), fileName)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        return file to uri
    }

    // Resultado de intentar copiar un video: fileName == null si falló Y
    // tooLarge indica la razón específica (para poder avisarle al usuario
    // "video demasiado pesado" en vez de un error genérico).
    data class VideoCopyResult(val fileName: String?, val tooLarge: Boolean = false)

    private fun guessVideoExtension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri) ?: return "mp4"
        return when {
            type.contains("webm") -> "webm"
            type.contains("3gpp") -> "3gp"
            type.contains("matroska") -> "mkv"
            else -> "mp4"
        }
    }

    // Copia un video seleccionado por el usuario, rechazándolo si supera
    // MAX_VIDEO_BYTES. No hay forma barata de saber el tamaño ANTES de copiar
    // (los content:// URI no siempre exponen el tamaño de forma confiable),
    // así que se copia primero y se borra después si resultó demasiado
    // pesado; es un poco de trabajo de más pero es la forma confiable.
    fun copyVideoFromUri(context: Context, uri: Uri): VideoCopyResult {
        return try {
            val extension = guessVideoExtension(context, uri)
            val fileName = newFileName(extension)
            val outFile = File(imagesDir(context), fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (outFile.length() > MAX_VIDEO_BYTES) {
                outFile.delete()
                VideoCopyResult(fileName = null, tooLarge = true)
            } else {
                VideoCopyResult(fileName = fileName)
            }
        } catch (e: Exception) {
            VideoCopyResult(fileName = null)
        }
    }

    fun deleteFile(context: Context, fileName: String) {
        try {
            File(imagesDir(context), fileName).delete()
        } catch (e: Exception) {
        }
    }

    // Ventana de gracia para cleanupOrphans: un archivo recién copiado que
    // todavía no aparece en NINGUNA nota (todavía no se guardó esa nota) NO
    // se toca hasta que pasen 24 horas desde que se creó — no hasta que se
    // confirme que está en uso. Ver el comentario grande de cleanupOrphans
    // para el porqué exacto de este número.
    private const val ORPHAN_GRACE_PERIOD_MS = 24L * 60L * 60L * 1000L // 24 horas

    // Barrido de limpieza de archivos huérfanos: borra de `imagesDir`
    // cualquier archivo que no esté en `referencedFileNames` — pensado para
    // correr una vez al arrancar la app (ver BouncyNotesApp.onCreate).
    //
    // Por qué hace falta esto en vez de confiar en que cada lugar que borra
    // un archivo se acuerde de hacerlo bien: el editor retrasa el borrado
    // real de una imagen/video 5 segundos para poder ofrecer "Deshacer" (ver
    // deleteMediaSegment en NoteEditScreen) — si el proceso muere en esos 5
    // segundos (cierre forzado desde Ajustes del sistema, el sistema mata la
    // app por memoria, un crash), ese archivo queda en el dispositivo sin
    // borrarse Y sin estar referenciado por ninguna nota: un huérfano. Un
    // temporizador en memoria (lo que había antes de este barrido) no tiene
    // forma de "terminar su trabajo" si el proceso entero desaparece —
    // depender de que algo en memoria sobreviva a un cierre forzado no
    // funciona nunca, sin importar qué tan bien esté escrito. Comparar
    // contra la realidad (qué archivos existen vs. qué archivos usa alguna
    // nota de verdad) en el próximo arranque es la única forma confiable de
    // recuperarse, sea cual sea la causa de que algo haya quedado suelto.
    //
    // OJO con la ventana de gracia de 24hs (ORPHAN_GRACE_PERIOD_MS): una
    // imagen recién insertada en una nota NUEVA (todavía sin guardar — acá
    // no hay autoguardado, se guarda al salir de la pantalla o al cambiar a
    // modo vista) existe como archivo en `imagesDir` ANTES de que la nota
    // que la referencia llegue a la base de datos. Si este barrido corriera
    // sin ninguna ventana de gracia justo en ese instante (la app se
    // reinicia mientras el usuario todavía está escribiendo esa nota nueva,
    // sin haber tocado atrás todavía), borraría una imagen que el usuario
    // recién insertó y todavía piensa guardar. Ignorar los archivos
    // modificados en las últimas 24hs evita ese falso positivo sin dejar de
    // limpiar lo que de verdad quedó huérfano — nada relacionado con una
    // sesión de edición normal tarda 24hs en resolverse.
    suspend fun cleanupOrphans(context: Context, referencedFileNames: Set<String>) {
        try {
            val cutoff = System.currentTimeMillis() - ORPHAN_GRACE_PERIOD_MS
            val files = imagesDir(context).listFiles() ?: return
            for (file in files) {
                if (file.name !in referencedFileNames && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
        }
    }

    // Lee SOLO las dimensiones del archivo (ancho x alto), sin decodificar
    // los píxeles — BitmapFactory.Options.inJustDecodeBounds hace que solo
    // se lea el encabezado del archivo, prácticamente instantáneo incluso
    // para imágenes grandes. Se usa para poder reservarle a una imagen su
    // relación de aspecto REAL en una lista con scroll (LazyColumn) antes
    // de que la imagen completa haya cargado — así el layout no "salta"
    // cuando la carga real termina, sin necesidad de recortar la imagen a
    // una proporción fija arbitraria.
    fun readImageDimensions(context: Context, fileName: String): Pair<Int, Int>? {
        return try {
            val file = File(imagesDir(context), fileName)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
        } catch (e: Exception) {
            null
        }
    }
}
