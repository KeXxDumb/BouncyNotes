package com.dumb.bouncynotes.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Formato de archivo propio de la app: .bnotes.
//
// Estructura del archivo (binario):
//   "BNOT1" (5 bytes) | versión (1 byte) | tamaño del IV (1 byte) | IV (12 bytes)
//   | JSON cifrado (AES-256-GCM, tag de 128 bits al final)
//
// El JSON contiene las notas (mismo formato que los respaldos .zip), los
// archivos de imagen/video embebidos en base64 y la fecha límite ABSOLUTA
// (epoch millis) elegida por quien envía. La fecha se guarda en el momento de
// compartir, no cuando el receptor abre el archivo.
//
// "Encriptado simple" a propósito: la clave se deriva de una frase fija que
// vive en la app. Sirve para que el archivo no sea legible a simple vista y
// para detectar modificaciones (GCM autentica el contenido), PERO no protege
// contra alguien que extraiga la clave del APK. Si en el futuro se quiere
// secreto real, lo correcto sería pedir una contraseña al que envía y
// derivar la clave con PBKDF2/Argon2.
object ShareFileManager {

    const val MIME_TYPE = "application/x-bouncynotes"
    const val EXTENSION = "bnotes"

    private const val MAGIC = "BNOT1"
    private const val FORMAT_VERSION = 1
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val PASSPHRASE = "BouncyNotes.ShareFile.v1"

    private val key: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256").digest(PASSPHRASE.toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    data class Payload(
        val createdAt: Long,
        val expiresAt: Long?,
        val notes: List<Note>,
        val media: Map<String, ByteArray>
    )

    sealed class ReadResult {
        data class Success(val payload: Payload) : ReadResult()
        object Expired : ReadResult()
        object Invalid : ReadResult()
    }

    fun createFile(context: Context, notes: List<Note>, expiresAt: Long?): File? = try {
        val root = File(context.cacheDir, "share_files").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(root, "bouncynotes_$stamp.$EXTENSION")

        val mediaNames = notes.flatMap { extractImageFileNames(it.content) }.distinct()
        val mediaObj = JSONObject()
        val imagesDir = ImageStorage.imagesDir(context)
        mediaNames.forEach { name ->
            val mediaFile = File(imagesDir, name)
            if (mediaFile.exists()) {
                mediaObj.put(name, Base64.encodeToString(mediaFile.readBytes(), Base64.NO_WRAP))
            }
        }

        val notesArray = JSONArray()
        notes.forEach { notesArray.put(BackupManager.noteToJson(it)) }

        val json = JSONObject().apply {
            put("format", FORMAT_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("expiresAt", expiresAt ?: JSONObject.NULL)
            put("notes", notesArray)
            put("media", mediaObj)
        }

        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))

        file.outputStream().use { out ->
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(FORMAT_VERSION)
            out.write(iv.size)
            out.write(iv)
            out.write(encrypted)
        }
        file
    } catch (e: Exception) {
        null
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun read(context: Context, uri: Uri): ReadResult = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ReadResult.Invalid
        val headerSize = MAGIC.length + 2 + IV_LENGTH
        if (bytes.size <= headerSize + 16) return ReadResult.Invalid
        if (String(bytes, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) return ReadResult.Invalid

        val version = bytes[MAGIC.length].toInt()
        if (version != FORMAT_VERSION) return ReadResult.Invalid

        val ivLength = bytes[MAGIC.length + 1].toInt()
        if (ivLength != IV_LENGTH) return ReadResult.Invalid

        val ivOffset = MAGIC.length + 2
        val iv = bytes.copyOfRange(ivOffset, ivOffset + ivLength)
        val cipherText = bytes.copyOfRange(ivOffset + ivLength, bytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val json = JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
        if (json.optInt("format", -1) != FORMAT_VERSION) return ReadResult.Invalid

        val expiresAt = if (json.isNull("expiresAt")) null else json.optLong("expiresAt")

        // Comprobación ANTES de devolver cualquier nota: si ya caducó, no se
        // puede ver ni importar. Además borramos la copia si el archivo está
        // en nuestro propio caché (flujo "Compartir" -> "Abrir con" local).
        if (expiresAt != null && System.currentTimeMillis() >= expiresAt) {
            deleteLocalCopyIfPossible(context, uri)
            return ReadResult.Expired
        }

        val notesArray = json.optJSONArray("notes") ?: return ReadResult.Invalid
        val notes = mutableListOf<Note>()
        for (i in 0 until notesArray.length()) {
            notes.add(BackupManager.jsonToNote(notesArray.getJSONObject(i)))
        }

        val media = mutableMapOf<String, ByteArray>()
        json.optJSONObject("media")?.let { mediaObj ->
            val keys = mediaObj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                media[name] = Base64.decode(mediaObj.getString(name), Base64.NO_WRAP)
            }
        }

        ReadResult.Success(
            Payload(
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                expiresAt = expiresAt,
                notes = notes,
                media = media
            )
        )
    } catch (e: Exception) {
        ReadResult.Invalid
    }

    // Escribe los medios con un prefijo propio por lote para no pisar fotos
    // de notas ya existentes, y reescribe las referencias dentro del contenido.
    fun prepareForImport(context: Context, payload: Payload): List<Note> {
        val batch = payload.expiresAt?.toString() ?: System.currentTimeMillis().toString()
        val prefix = "shared_${batch}_"
        val renamed = payload.media.keys.associateWith { prefix + it }
        val imagesDir = ImageStorage.imagesDir(context)

        payload.media.forEach { (name, bytes) ->
            File(imagesDir, renamed.getValue(name)).writeBytes(bytes)
        }

        return payload.notes.map { note ->
            var content = note.content
            renamed.forEach { (oldName, newName) -> content = content.replace(oldName, newName) }
            note.copy(
                id = 0,
                content = content,
                expiresAt = payload.expiresAt,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                deletedAt = null,
                pinned = false
            )
        }
    }

    fun deleteLocalCopyIfPossible(context: Context, uri: Uri) {
        if (uri.scheme != "file") return
        val path = uri.path ?: return
        val cacheRoot = File(context.cacheDir, "share_files").absolutePath
        if (path.startsWith(cacheRoot)) runCatching { File(path).delete() }
    }
}
