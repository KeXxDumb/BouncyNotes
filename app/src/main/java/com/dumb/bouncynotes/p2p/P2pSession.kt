package com.dumb.bouncynotes.p2p

import android.content.Context
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.extractMediaRefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Compartir una COPIA TEMPORAL de una nota entre dos teléfonos, por internet y sin
// servidor propio. Usa WebRTC solo por su canal de datos (nada de audio ni video):
//  - Los dos teléfonos se encuentran intercambiando a mano dos "códigos" (la oferta del
//    que comparte y la respuesta del que recibe; ver P2pCodec). No hay servidor de
//    señalización: por eso se espera a que termine la búsqueda de rutas (ICE) y el
//    código ya lleva todas las rutas dentro (sin "trickle").
//  - Para descubrir su dirección pública usan STUN gratuito (Cloudflare y Google). Sin un
//    servidor de relevo (TURN) la conexión directa a veces no se logra, sobre todo
//    con datos móviles: en ese caso se avisa que se pruebe con Wi-Fi.
//  - El canal va cifrado (DTLS) de punta a punta.
// El que RECIBE la ve en solo lectura mientras la conexión siga abierta; nada se guarda
// en su base de datos hasta que toque "Guardar copia" (saveReceived). Los archivos de
// imágenes/videos recibidos van a una carpeta temporal de caché que se borra al cerrar.
//
// Protocolo sobre el canal (los mensajes de texto son JSON, los binarios son trozos de archivo):
//   {"t":"note", type, title, color, createdAt, skipped, files:[{name,size}...]}
//   {"t":"c","d":"<trozo del contenido>"}   (cuantos hagan falta: un mensaje SCTP no pasa de ~256 KB)
//   {"t":"file","name":..., "size":...} seguido de trozos binarios hasta completar `size`
//   {"t":"end"}
class P2pSession(context: Context) {

    enum class Phase { IDLE, GATHERING, WAITING_FOR_PEER, CONNECTED, CLOSED, FAILED }

    // Una nota ya recibida por completo. `files` apunta a los archivos temporales.
    class ReceivedNote(
        val type: NoteType,
        val title: String,
        val content: String,
        val color: String?,
        val createdAt: Long,
        val files: Map<String, File>,
        val skippedCount: Int
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val channelOpened = AtomicBoolean(false)

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress
    private val _received = MutableStateFlow<ReceivedNote?>(null)
    val received: StateFlow<ReceivedNote?> = _received

    private val gathering = CompletableDeferred<Unit>()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var channel: DataChannel? = null
    private var noteToSend: Note? = null

    // --- Estado del lado que recibe ---
    private var tempDir: File? = null
    private var metaType = NoteType.TEXT
    private var metaTitle = ""
    private var metaColor: String? = null
    private var metaCreatedAt = 0L
    private var metaSkipped = 0
    private var expectedTotalBytes = 0L
    private var receivedBytes = 0L
    private val contentBuilder = StringBuilder()
    private val receivedFiles = LinkedHashMap<String, File>()
    private var currentOut: FileOutputStream? = null
    private var currentFile: File? = null
    private var currentName: String? = null
    private var currentRemaining = 0L

    // ------------------------------------------------------------------
    // Lado que COMPARTE
    // ------------------------------------------------------------------

    // Prepara la conexión y devuelve el código (oferta) para mandarle a la otra persona.
    suspend fun startAsSender(note: Note): String {
        noteToSend = note
        _phase.value = Phase.GATHERING
        _status.value = "Preparando el código…"
        val connection = createPeerConnection()
        val dataChannel = connection.createDataChannel("bouncynotes", DataChannel.Init())
            ?: throw IllegalStateException("No se pudo crear el canal de datos")
        attachChannel(dataChannel)
        val offer = connection.awaitCreateOffer()
        connection.awaitSetLocal(offer)
        waitForGathering(connection)
        val local = connection.localDescription ?: offer
        _phase.value = Phase.WAITING_FOR_PEER
        _status.value = "Esperando la respuesta de la otra persona…"
        return P2pCodec.encode("offer", local.description)
    }

    // Recibe el código de respuesta de la otra persona y completa la conexión.
    suspend fun finishAsSender(answerCode: String) {
        val signal = P2pCodec.decode(answerCode)
            ?: throw IllegalArgumentException("El código no es válido. Revisa que esté completo.")
        if (signal.first != "answer") {
            throw IllegalArgumentException("Ese es un código de oferta. Aquí hace falta el código de RESPUESTA.")
        }
        val connection = peer ?: throw IllegalStateException("La conexión ya se cerró")
        connection.awaitSetRemote(SessionDescription(SessionDescription.Type.ANSWER, signal.second))
        _status.value = "Conectando…"
        startConnectTimeout()
    }

    // ------------------------------------------------------------------
    // Lado que RECIBE
    // ------------------------------------------------------------------

    // Recibe el código (oferta) de quien comparte y devuelve el código de respuesta.
    suspend fun startAsReceiver(offerCode: String): String {
        val signal = P2pCodec.decode(offerCode)
            ?: throw IllegalArgumentException("El código no es válido. Revisa que esté completo.")
        if (signal.first != "offer") {
            throw IllegalArgumentException("Ese es un código de respuesta. Aquí hace falta el código de OFERTA.")
        }
        _phase.value = Phase.GATHERING
        _status.value = "Preparando la respuesta…"
        val connection = createPeerConnection()
        connection.awaitSetRemote(SessionDescription(SessionDescription.Type.OFFER, signal.second))
        val answer = connection.awaitCreateAnswer()
        connection.awaitSetLocal(answer)
        waitForGathering(connection)
        val local = connection.localDescription ?: answer
        _phase.value = Phase.WAITING_FOR_PEER
        _status.value = "Esperando que la otra persona pegue tu código…"
        startConnectTimeout()
        return P2pCodec.encode("answer", local.description)
    }

    // Guarda la copia recibida como nota NUEVA: mueve los archivos de la carpeta
    // temporal a la de imágenes (con nombres nuevos, para no pisar nada) y reescribe
    // las referencias en el contenido. Devuelve la nota lista para guardar en la BD.
    fun buildNoteToSave(): Note? {
        val note = _received.value ?: return null
        var content = note.content
        val now = System.currentTimeMillis()
        note.files.forEach { (name, file) ->
            val extension = name.substringAfterLast('.', "jpg")
            val newName = ImageStorage.newFileName(extension)
            try {
                file.copyTo(File(ImageStorage.imagesDir(appContext), newName), overwrite = false)
            } catch (e: Exception) {
                return@forEach
            }
            // El nombre aparece después de ':' o ',' y termina en '|', ',' o ']'.
            val reference = Regex("(?<=[:,])" + Regex.escape(name) + "(?=[|,\\]])")
            content = content.replace(reference, newName)
        }
        return Note(
            type = note.type,
            title = note.title,
            content = content,
            color = note.color,
            // Un cuaderno conserva su fecha (es la que lo ubica en el tiempo); una nota normal nace ahora.
            createdAt = if (note.type == NoteType.JOURNAL && note.createdAt > 0) note.createdAt else now,
            updatedAt = now
        )
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { currentOut?.close() }
        runCatching { channel?.close() }
        runCatching { channel?.dispose() }
        runCatching { peer?.close() }
        runCatching { peer?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { tempDir?.deleteRecursively() }
        scope.cancel()
        if (_phase.value != Phase.FAILED) _phase.value = Phase.CLOSED
    }

    // ------------------------------------------------------------------
    // WebRTC
    // ------------------------------------------------------------------

    private val peerObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            if (newState == PeerConnection.IceConnectionState.FAILED) {
                fail("No se pudo conectar. A veces pasa con datos móviles: prueba con Wi-Fi.")
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            if (newState == PeerConnection.PeerConnectionState.FAILED) {
                fail("No se pudo conectar. A veces pasa con datos móviles: prueba con Wi-Fi.")
            } else if (newState == PeerConnection.PeerConnectionState.DISCONNECTED ||
                newState == PeerConnection.PeerConnectionState.CLOSED
            ) {
                markClosed()
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            if (newState == PeerConnection.IceGatheringState.COMPLETE) gathering.complete(Unit)
        }

        // Sin "trickle": las rutas ya van todas dentro del código (ver waitForGathering).
        override fun onIceCandidate(candidate: IceCandidate?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {}

        // El lado que recibe se entera del canal acá (lo creó quien comparte).
        override fun onDataChannel(dataChannel: DataChannel?) {
            if (dataChannel != null) attachChannel(dataChannel)
        }
    }

    private fun createPeerConnection(): PeerConnection {
        ensureWebRtcInitialized(appContext)
        val newFactory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        factory = newFactory
        val servers = listOf(
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val connection = newFactory.createPeerConnection(config, peerObserver)
            ?: throw IllegalStateException("No se pudo crear la conexión")
        peer = connection
        return connection
    }

    // Espera a que termine la búsqueda de rutas para que el código lleve TODAS (hay
    // un tope de 6 s por si alguna ruta tarda: se usan las que haya hasta ese momento).
    private suspend fun waitForGathering(connection: PeerConnection) {
        if (connection.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) return
        withTimeoutOrNull(6000) { gathering.await() }
    }

    private fun startConnectTimeout() {
        scope.launch {
            delay(90_000)
            if (_phase.value != Phase.CONNECTED && !closed.get()) {
                fail("No se pudo conectar. A veces pasa con datos móviles: prueba con Wi-Fi.")
            }
        }
    }

    private fun attachChannel(dataChannel: DataChannel) {
        channel = dataChannel
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                when (dataChannel.state()) {
                    DataChannel.State.OPEN -> onChannelOpen()
                    DataChannel.State.CLOSED -> markClosed()
                    else -> {}
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                if (buffer.binary) handleBinary(bytes) else handleText(String(bytes, Charsets.UTF_8))
            }
        })
        // Puede que ya esté abierto cuando se registra el observador.
        if (dataChannel.state() == DataChannel.State.OPEN) onChannelOpen()
    }

    private fun onChannelOpen() {
        if (!channelOpened.compareAndSet(false, true)) return
        _phase.value = Phase.CONNECTED
        val note = noteToSend
        if (note != null) {
            _status.value = "Conectado. Enviando la copia…"
            scope.launch { sendNote(note) }
        } else {
            _status.value = "Conectado. Esperando la nota…"
        }
    }

    private fun markClosed() {
        if (closed.get()) return
        val phase = _phase.value
        if (phase != Phase.FAILED && phase != Phase.CLOSED) {
            _phase.value = Phase.CLOSED
            _status.value = "La conexión se cerró."
        }
    }

    private fun fail(message: String) {
        if (closed.get()) return
        _phase.value = Phase.FAILED
        _status.value = message
    }

    // ------------------------------------------------------------------
    // Envío
    // ------------------------------------------------------------------

    private suspend fun waitForBuffer(dc: DataChannel) {
        while (dc.bufferedAmount() > MAX_BUFFERED) delay(20)
    }

    private suspend fun sendText(dc: DataChannel, text: String) {
        waitForBuffer(dc)
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false))
    }

    private suspend fun sendNote(note: Note) {
        val dc = channel ?: return
        try {
            val dir = ImageStorage.imagesDir(appContext)
            // Los videos NO se envían: la copia temporal no los reproduce (pesan mucho y el
            // canal de datos es lento); en el visor aparecen como un aviso.
            val candidates = extractMediaRefs(note.content).filter { !it.isVideo }.map { it.fileName }.distinct()
            val chosen = mutableListOf<Pair<String, File>>()
            var total = 0L
            for (name in candidates) {
                val file = File(dir, name)
                val size = if (file.isFile) file.length() else 0L
                if (size <= 0L || size > MAX_FILE_BYTES || total + size > MAX_TOTAL_BYTES) continue
                total += size
                chosen.add(name to file)
            }
            val skipped = candidates.size - chosen.size

            val files = JSONArray()
            chosen.forEach { (name, file) -> files.put(JSONObject().put("name", name).put("size", file.length())) }
            val meta = JSONObject()
                .put("t", "note")
                .put("v", 1)
                .put("type", note.type.name)
                .put("title", note.title)
                .put("color", note.color ?: JSONObject.NULL)
                .put("createdAt", note.createdAt)
                .put("skipped", skipped)
                .put("files", files)
            sendText(dc, meta.toString())

            var offset = 0
            while (offset < note.content.length) {
                val end = minOf(note.content.length, offset + CONTENT_CHUNK_CHARS)
                sendText(dc, JSONObject().put("t", "c").put("d", note.content.substring(offset, end)).toString())
                offset = end
            }

            var sent = 0L
            for ((name, file) in chosen) {
                sendText(dc, JSONObject().put("t", "file").put("name", name).put("size", file.length()).toString())
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(FILE_CHUNK_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        waitForBuffer(dc)
                        dc.send(DataChannel.Buffer(ByteBuffer.wrap(buffer.copyOf(n)), true))
                        sent += n
                        _progress.value = if (total > 0) sent.toFloat() / total else 1f
                    }
                }
            }
            sendText(dc, """{"t":"end"}""")
            // Que se vacíe el buffer antes de decir "listo" (si se cierra antes, se pierde el final).
            while (dc.bufferedAmount() > 0 && !closed.get()) delay(50)
            _progress.value = 1f
            _status.value = "Copia enviada. La otra persona la ve mientras sigan conectados."
        } catch (e: Exception) {
            fail("No se pudo enviar la copia: ${e.message ?: "error desconocido"}")
        }
    }

    // ------------------------------------------------------------------
    // Recepción
    // ------------------------------------------------------------------

    private fun handleText(text: String) {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        try {
            when (json.optString("t")) {
                "note" -> {
                    val files = json.optJSONArray("files") ?: JSONArray()
                    var total = 0L
                    if (files.length() > MAX_FILES) throw IllegalStateException("Demasiados archivos")
                    for (i in 0 until files.length()) {
                        val size = files.getJSONObject(i).optLong("size")
                        if (size < 0 || size > MAX_FILE_BYTES) throw IllegalStateException("Archivo demasiado grande")
                        total += size
                    }
                    if (total > MAX_TOTAL_BYTES) throw IllegalStateException("La copia es demasiado grande")
                    expectedTotalBytes = total
                    metaType = if (json.optString("type") == NoteType.JOURNAL.name) NoteType.JOURNAL else NoteType.TEXT
                    metaTitle = json.optString("title")
                    metaColor = if (json.isNull("color")) null else json.optString("color").takeIf { it.isNotEmpty() }
                    metaCreatedAt = json.optLong("createdAt")
                    metaSkipped = json.optInt("skipped")
                    tempDir = File(appContext.cacheDir, "p2p_${System.currentTimeMillis()}").also { it.mkdirs() }
                    contentBuilder.setLength(0)
                    _status.value = "Recibiendo la copia…"
                }
                "c" -> {
                    if (contentBuilder.length < MAX_CONTENT_CHARS) contentBuilder.append(json.optString("d"))
                }
                "file" -> {
                    closeCurrentFile()
                    val name = json.optString("name")
                    val size = json.optLong("size")
                    val dir = tempDir ?: throw IllegalStateException("Mensaje fuera de orden")
                    // Nombres estrictos: sin rutas ni caracteres raros (otro teléfono NO es de fiar).
                    if (!SAFE_FILE_NAME.matches(name) || size < 0 || size > MAX_FILE_BYTES) {
                        throw IllegalStateException("Archivo no válido")
                    }
                    val file = File(dir, name)
                    currentFile = file
                    currentName = name
                    currentOut = FileOutputStream(file)
                    currentRemaining = size
                    if (size == 0L) closeCurrentFile()
                }
                "end" -> {
                    closeCurrentFile()
                    _received.value = ReceivedNote(
                        type = metaType,
                        title = metaTitle,
                        content = contentBuilder.toString(),
                        color = metaColor,
                        createdAt = metaCreatedAt,
                        files = LinkedHashMap(receivedFiles),
                        skippedCount = metaSkipped
                    )
                    _status.value = "Copia recibida."
                }
            }
        } catch (e: Exception) {
            fail("La copia recibida no es válida: ${e.message ?: "error"}")
        }
    }

    private fun handleBinary(bytes: ByteArray) {
        val out = currentOut ?: return
        if (bytes.size > currentRemaining) {
            fail("La copia recibida no es válida: archivo más largo de lo anunciado")
            return
        }
        try {
            out.write(bytes)
        } catch (e: Exception) {
            fail("No se pudo guardar el archivo recibido")
            return
        }
        currentRemaining -= bytes.size
        receivedBytes += bytes.size
        if (expectedTotalBytes > 0) _progress.value = (receivedBytes.toFloat() / expectedTotalBytes).coerceAtMost(1f)
        if (currentRemaining == 0L) closeCurrentFile()
    }

    private fun closeCurrentFile() {
        runCatching { currentOut?.close() }
        val name = currentName
        val file = currentFile
        if (name != null && file != null && currentRemaining == 0L) receivedFiles[name] = file
        currentOut = null
        currentFile = null
        currentName = null
    }

    // ------------------------------------------------------------------
    // Ayudas de WebRTC (callbacks -> corrutinas)
    // ------------------------------------------------------------------

    private abstract class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }

    private suspend fun PeerConnection.awaitCreateOffer(): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) cont.resume(sdp)
                    else cont.resumeWithException(IllegalStateException("No se pudo crear la oferta"))
                }

                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "No se pudo crear la oferta"))
                }
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.awaitCreateAnswer(): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createAnswer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) cont.resume(sdp)
                    else cont.resumeWithException(IllegalStateException("No se pudo crear la respuesta"))
                }

                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "No se pudo crear la respuesta"))
                }
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.awaitSetLocal(sdp: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            setLocalDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "No se pudo aplicar la descripción local"))
                }
            }, sdp)
        }

    private suspend fun PeerConnection.awaitSetRemote(sdp: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }

                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "El código no es compatible"))
                }
            }, sdp)
        }

    companion object {
        private const val MAX_BUFFERED = 1_000_000L
        private const val FILE_CHUNK_BYTES = 16 * 1024
        private const val CONTENT_CHUNK_CHARS = 60_000
        private const val MAX_CONTENT_CHARS = 5_000_000
        private const val MAX_FILE_BYTES = 32L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        private const val MAX_FILES = 300
        private val SAFE_FILE_NAME = Regex("[A-Za-z0-9_\\-]{1,80}\\.[A-Za-z0-9]{1,5}")

        @Volatile
        private var initialized = false

        // La inicialización de WebRTC (carga de la librería nativa) se hace una sola vez por proceso.
        private fun ensureWebRtcInitialized(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
                )
                initialized = true
            }
        }
    }
}
