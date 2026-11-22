package com.dumb.bouncynotes.p2p

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// Formato del "código" que dos personas se pasan a mano (por WhatsApp, etc.) para
// conectarse sin servidor: la descripción de conexión de WebRTC (SDP), en JSON,
// comprimida con gzip y en base64 URL-safe, con el prefijo "BN1:" para reconocerla
// aunque venga con texto de más alrededor. Mide unos cientos de caracteres.
object P2pCodec {
    private const val PREFIX = "BN1:"
    private const val MAX_DECODED_BYTES = 100_000 // tope contra un código malicioso

    fun encode(type: String, sdp: String): String {
        val json = JSONObject().put("t", type).put("sdp", sdp).toString()
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return PREFIX + Base64.encodeToString(
            out.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    // Devuelve (tipo, sdp) o null si el código no es válido. Tolera espacios y
    // saltos de línea (los chats suelen partir los textos largos) y texto antes/después.
    fun decode(code: String): Pair<String, String>? {
        val clean = code.filterNot { it.isWhitespace() }
        val start = clean.indexOf(PREFIX)
        if (start < 0) return null
        return try {
            val bytes = Base64.decode(
                clean.substring(start + PREFIX.length),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val out = ByteArrayOutputStream()
            GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_DECODED_BYTES) return null
                }
            }
            val json = JSONObject(out.toString("UTF-8"))
            json.getString("t") to json.getString("sdp")
        } catch (e: Exception) {
            null
        }
    }
}
