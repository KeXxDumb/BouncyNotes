package com.dumb.bouncynotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

// Easter egg "pack de texturas" (Opción B — ver el chat: se descartó a
// propósito la otra opción, un ícono oculto ya compilado en el APK
// desbloqueado con una llave, porque el pedido explícito era que el
// contenido de verdad SE IMPORTE, no que viva de antemano en el código).
//
// Reusa el mismo botón "Importar" de siempre (ver SettingsScreen.kt): un
// .zip con "bouncynotes-pack.json" en la raíz, DISTINTO de un respaldo de
// notas normal (que trae "notes.json"). Formato exacto y reglas en
// bouncynotes-texture-pack-template.zip (LEEME.txt), ya entregado antes:
//   { "type": "bouncynotes-texture-pack", "formatVersion": 1,
//     "textures": { "peach": "peach.png" } }
//
// El resultado (el archivo de imagen ya procesado) se guarda en una carpeta
// PROPIA del dispositivo (texturesDir), separada a propósito de
// ImageStorage.imagesDir: así nunca se mezcla con los archivos de las notas
// ni lo toca el barrido de huérfanos (ImageStorage.cleanupOrphans), que solo
// mira esa otra carpeta.
object TexturePackManager {

    // Por archivo suelto dentro del zip (8MB de sobra para un ícono chico) y
    // en total (20MB) — cualquiera de los dos que se pase, se aborta todo.
    // Es la defensa contra un zip armado a propósito para agotar memoria
    // ("zip bomb" liviana): sin esto, un solo entry gigante se leería
    // entero en memoria sin límite.
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 20 * 1024 * 1024

    // Lado más largo de la textura ya guardada — un poco más grande que
    // donde se usa hoy (el botón de 28dp de la barra superior) para que no
    // se vea pixelada si en el futuro se reutiliza en un lugar más grande,
    // pero sin dejar pasar una imagen de resolución absurda tal cual venía.
    private const val MAX_IMAGE_DIMENSION = 512

    fun texturesDir(context: Context): File =
        File(context.filesDir, "texture_packs").apply { mkdirs() }

    fun peachTextureFile(context: Context): File = File(texturesDir(context), "peach.png")

    // Devuelve el path absoluto de la textura ya guardada si el zip era un
    // pack válido con una textura "peach", o null si no lo era (incluido
    // "es un respaldo de notas normal", que es el caso más común) o si algo
    // en el camino no pasó las validaciones. Nunca tira excepción hacia
    // afuera.
    fun tryImportPeachTexture(context: Context, srcUri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                extractPeachTexture(context, input)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPeachTexture(context: Context, input: InputStream): String? {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                // Todo tiene que vivir en la RAÍZ del zip (ver LEEME.txt de
                // la plantilla) — un nombre con separadores de carpeta o
                // ".." se rechaza directo, no se ignora nomás, porque
                // permitirlo abriría la puerta a escribir fuera de
                // texturesDir ("zip slip").
                if (name.contains('/') || name.contains('\\') || name.contains("..")) {
                    return null
                }
                val bytes = readBounded(zip, MAX_ENTRY_BYTES) ?: return null
                totalBytes += bytes.size
                if (totalBytes > MAX_TOTAL_BYTES) return null
                entries[name] = bytes
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifestBytes = entries["bouncynotes-pack.json"] ?: return null
        val manifest = try {
            JSONObject(manifestBytes.decodeToString())
        } catch (e: Exception) {
            return null
        }
        if (manifest.optString("type") != "bouncynotes-texture-pack") return null
        val textures = manifest.optJSONObject("textures") ?: return null
        val peachFileName = textures.optString("peach").takeIf { it.isNotBlank() } ?: return null
        val peachBytes = entries[peachFileName] ?: return null

        // Límite de tamaño ANTES de decodificar la imagen entera —
        // inJustDecodeBounds solo lee el encabezado, así que esto es
        // barato incluso si la imagen real es enorme.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(peachBytes, 0, peachBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_DIMENSION || bounds.outHeight / sample > MAX_IMAGE_DIMENSION) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            peachBytes,
            0,
            peachBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val outFile = peachTextureFile(context)
        outFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        return outFile.absolutePath
    }

    // Lee como mucho `max` bytes de `input` — si el contenido real sigue
    // después de eso, corta ahí y devuelve null (no un recorte silencioso),
    // en vez de seguir leyendo sin límite.
    private fun readBounded(input: InputStream, max: Int): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > max) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
