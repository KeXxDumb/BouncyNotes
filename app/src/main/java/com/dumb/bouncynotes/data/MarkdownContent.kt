package com.dumb.bouncynotes.data

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

// Sintaxis ligera al estilo Markdown para contenido embebido dentro del texto de una nota:
// [[img:nombre_de_archivo.jpg|descripción opcional]]           -> una sola imagen (o gif)
// [[gallery:LAYOUT:archivo1.jpg,archivo2.jpg,archivo3.jpg]]    -> varias imágenes agrupadas
// [[video:nombre_de_archivo.mp4|descripción opcional]]         -> un video
// Los archivos siempre viven en ImageStorage.imagesDir(context), así que solo se guarda el nombre.

// Formatos de vista disponibles para un grupo de imágenes. Configurable en
// Ajustes (formato por defecto) y elegido en el momento al insertar el grupo.
enum class GalleryLayout(val label: String) {
    GRID_2("Cuadrícula de 2"),
    GRID_3("Cuadrícula de 3"),
    CAROUSEL("Carrusel horizontal")
}

sealed class ContentPart {
    data class TextPart(val text: String) : ContentPart()
    data class ImagePart(val fileName: String, val caption: String) : ContentPart()
    // captions viene siempre alineada 1 a 1 con fileNames por índice
    // (mismo tamaño); una entrada vacía "" significa "sin descripción". Se
    // agregó como parámetro con default para no romper el único lugar que
    // construye esto (el parser, más abajo) ni nada que solo hiciera
    // pattern-matching sobre fileNames/layout.
    data class GalleryPart(val layout: GalleryLayout, val fileNames: List<String>, val captions: List<String> = emptyList()) : ContentPart() {
        fun captionAt(index: Int): String = captions.getOrElse(index) { "" }
    }
    data class VideoPart(val fileName: String, val caption: String) : ContentPart()
}

// Un ítem cualquiera de la lista PLANA de medios de una nota (para el visor a
// pantalla completa, que necesita saber si cada uno es imagen o video para
// decidir cómo renderizarlo).
data class MediaRef(val fileName: String, val isVideo: Boolean, val caption: String = "")

private val imageTagRegex = Regex("""\[\[img:([^|\]]+)\|([^\]]*)\]\]""")
private val galleryTagRegex = Regex("""\[\[gallery:([A-Z0-9_]+):([^\]]*)\]\]""")
private val videoTagRegex = Regex("""\[\[video:([^|\]]+)\|([^\]]*)\]\]""")
// Combinada, para recorrer el contenido UNA sola vez y mantener el orden real
// de aparición de imágenes sueltas, grupos y videos mezclados. Orden de
// grupos de captura: 1-2 imagen, 3-4 grupo, 5-6 video.
private val combinedTagRegex = Regex(
    """${imageTagRegex.pattern}|${galleryTagRegex.pattern}|${videoTagRegex.pattern}"""
)

fun buildImageTag(fileName: String, caption: String = ""): String =
    "[[img:$fileName|$caption]]"

fun buildGalleryTag(layout: GalleryLayout, fileNames: List<String>, captions: List<String> = emptyList()): String {
    // Cada archivo se guarda como "nombre.jpg|descripción" SOLO si tiene
    // descripción — si no, queda "nombre.jpg" tal cual como antes. Así las
    // notas ya guardadas (sin ninguna descripción de grupo) no cambian ni
    // un carácter al volver a guardarse, y el formato viejo (que el parser
    // de abajo sigue leyendo sin problema) sigue siendo válido.
    val entries = fileNames.mapIndexed { i, name ->
        val caption = captions.getOrElse(i) { "" }
        if (caption.isEmpty()) name else "$name|$caption"
    }
    return "[[gallery:${layout.name}:${entries.joinToString(",")}]]"
}

fun buildVideoTag(fileName: String, caption: String = ""): String =
    "[[video:$fileName|$caption]]"

private fun parseGalleryLayout(raw: String): GalleryLayout =
    runCatching { GalleryLayout.valueOf(raw) }.getOrDefault(GalleryLayout.GRID_2)

private fun matchKind(value: String): String = when {
    value.startsWith("[[img:") -> "img"
    value.startsWith("[[gallery:") -> "gallery"
    else -> "video"
}

fun parseNoteContent(content: String): List<ContentPart> {
    val result = mutableListOf<ContentPart>()
    var lastIndex = 0
    for (match in combinedTagRegex.findAll(content)) {
        if (match.range.first > lastIndex) {
            result.add(ContentPart.TextPart(content.substring(lastIndex, match.range.first)))
        }
        when (matchKind(match.value)) {
            "img" -> result.add(ContentPart.ImagePart(fileName = match.groupValues[1], caption = match.groupValues[2]))
            "gallery" -> {
                val rawEntries = match.groupValues[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (rawEntries.isNotEmpty()) {
                    val fileNames = rawEntries.map { it.substringBefore('|') }
                    val captions = rawEntries.map { it.substringAfter('|', "") }
                    result.add(ContentPart.GalleryPart(parseGalleryLayout(match.groupValues[3]), fileNames, captions))
                }
            }
            else -> result.add(ContentPart.VideoPart(fileName = match.groupValues[5], caption = match.groupValues[6]))
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < content.length) {
        result.add(ContentPart.TextPart(content.substring(lastIndex)))
    }
    return result
}

// Lista plana de TODOS los nombres de archivo (imagen, video, y las que están
// dentro de un grupo) de la nota, en orden real de aparición. La usan tanto
// la limpieza de archivos al borrar la nota como isNoteEmpty.
fun extractImageFileNames(content: String): List<String> =
    parseNoteContent(content).flatMap { part ->
        when (part) {
            is ContentPart.ImagePart -> listOf(part.fileName)
            is ContentPart.GalleryPart -> part.fileNames
            is ContentPart.VideoPart -> listOf(part.fileName)
            is ContentPart.TextPart -> emptyList()
        }
    }

// Igual que extractImageFileNames, pero indicando cuáles son video: la usa el
// visor a pantalla completa para decidir si renderiza un reproductor o una
// imagen para cada página.
fun extractMediaRefs(content: String): List<MediaRef> =
    parseNoteContent(content).flatMap { part ->
        when (part) {
            is ContentPart.ImagePart -> listOf(MediaRef(part.fileName, isVideo = false, caption = part.caption))
            is ContentPart.GalleryPart -> part.fileNames.mapIndexed { i, name ->
                MediaRef(name, isVideo = false, caption = part.captionAt(i))
            }
            is ContentPart.VideoPart -> listOf(MediaRef(part.fileName, isVideo = true, caption = part.caption))
            is ContentPart.TextPart -> emptyList()
        }
    }

// occurrenceIndex se refiere a la posición de la imagen dentro de la lista plana
// que devuelve extractImageFileNames (0-based): cuenta imágenes sueltas y las
// que están dentro de un grupo por igual. Los videos no tienen descripción
// editable desde acá (no hace falta, no tienen un campo de caption editable
// en el editor todavía), por eso esta función sigue enfocada solo en imágenes.
//
// Antes esto SOLO tocaba imágenes sueltas (imageTagRegex.replace) — si
// occurrenceIndex caía en una imagen dentro de un grupo, no hacía nada en
// silencio, a pesar de que el comentario de arriba siempre dijo que debía
// cubrir ambos casos. Ahora recorre igual que removeImageOccurrence (con
// combinedTagRegex) para manejar los dos casos de verdad.
fun updateImageCaption(content: String, occurrenceIndex: Int, newCaption: String): String {
    val sb = StringBuilder()
    var lastIndex = 0
    var flatIndex = 0
    for (match in combinedTagRegex.findAll(content)) {
        sb.append(content, lastIndex, match.range.first)
        when (matchKind(match.value)) {
            "img" -> {
                if (flatIndex == occurrenceIndex) {
                    sb.append(buildImageTag(match.groupValues[1], newCaption))
                } else {
                    sb.append(match.value)
                }
                flatIndex++
            }
            "video" -> {
                sb.append(match.value)
                flatIndex++
            }
            else -> {
                val layout = match.groupValues[3]
                val rawEntries = match.groupValues[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val fileNames = rawEntries.map { it.substringBefore('|') }
                val localIndex = occurrenceIndex - flatIndex
                if (localIndex in fileNames.indices) {
                    val captions = rawEntries.map { it.substringAfter('|', "") }.toMutableList()
                    captions[localIndex] = newCaption
                    sb.append(buildGalleryTag(parseGalleryLayout(layout), fileNames, captions))
                } else {
                    sb.append(match.value)
                }
                flatIndex += fileNames.size
            }
        }
        lastIndex = match.range.last + 1
    }
    sb.append(content, lastIndex, content.length)
    return sb.toString()
}

// Quita UN ítem de la lista plana (occurrenceIndex): si es una imagen o video
// suelto, quita el tag completo; si es una imagen dentro de un grupo, la saca
// solo a ella de la lista del grupo (y si era la última que quedaba, quita el
// grupo entero).
fun removeImageOccurrence(content: String, occurrenceIndex: Int): String {
    val sb = StringBuilder()
    var lastIndex = 0
    var flatIndex = 0
    for (match in combinedTagRegex.findAll(content)) {
        sb.append(content, lastIndex, match.range.first)
        when (matchKind(match.value)) {
            "img", "video" -> {
                if (flatIndex != occurrenceIndex) {
                    sb.append(match.value)
                }
                flatIndex++
            }
            else -> {
                val layout = match.groupValues[3]
                val rawEntries = match.groupValues[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val fileNames = rawEntries.map { it.substringBefore('|') }
                val captions = rawEntries.map { it.substringAfter('|', "") }
                val keepIndices = fileNames.indices.filter { i -> flatIndex + i != occurrenceIndex }
                val remainingFiles = keepIndices.map { fileNames[it] }
                val remainingCaptions = keepIndices.map { captions[it] }
                when {
                    remainingFiles.isEmpty() -> { /* se quita el grupo entero */ }
                    // Un grupo con una sola imagen ya no es "grupo": lo dejamos
                    // como una imagen suelta normal en vez de una cuadrícula de 1,
                    // conservando su descripción si tenía una.
                    remainingFiles.size == 1 -> sb.append(buildImageTag(remainingFiles[0], remainingCaptions[0]))
                    else -> sb.append(buildGalleryTag(parseGalleryLayout(layout), remainingFiles, remainingCaptions))
                }
                flatIndex += fileNames.size
            }
        }
        lastIndex = match.range.last + 1
    }
    sb.append(content, lastIndex, content.length)
    return sb.toString()
}

// Para previews en la lista: quita los marcadores de formato para que se lea limpio.
fun stripFormattingMarkers(text: String): String =
    text.replace("**", "").replace("~~", "").replace("`", "").replace("*", "")

// Mismo algoritmo (mismos 4 marcadores) que buildInlineAnnotatedString en
// ui/components/MarkdownText.kt, pero produciendo un CharSequence con Spans
// de Android en vez de un AnnotatedString de Compose — un widget de home
// screen (RemoteViews) no puede usar Compose para nada, ni para el texto.
// Antes de esto, un widget con "**texto**" en la nota mostraba literalmente
// los asteriscos en vez de negrita — nunca se llegó a parsear el marcador,
// solo se mostraba el texto crudo de la nota (ver getNoteWidgetTextRowView).
// No incluye links clicables (a diferencia de InlineMarkdownText): una fila
// de RemoteViews solo puede tener UN fill-in Intent (ver bug #documentado
// en NoteWidgetContent.kt sobre dos intents en la misma fila), que ya está
// ocupado abriendo la nota completa.
private fun findClosingMarker(text: String, from: Int, marker: String): Int? {
    val idx = text.indexOf(marker, from)
    return if (idx == -1 || idx == from) null else idx
}

fun buildInlineSpannable(text: String): CharSequence {
    val builder = SpannableStringBuilder()
    var i = 0
    val n = text.length
    while (i < n) {
        if (text.startsWith("**", i)) {
            val close = findClosingMarker(text, i + 2, "**")
            if (close != null) {
                val start = builder.length
                builder.append(text.substring(i + 2, close))
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, 0)
                i = close + 2
                continue
            }
        }
        if (text.startsWith("~~", i)) {
            val close = findClosingMarker(text, i + 2, "~~")
            if (close != null) {
                val start = builder.length
                builder.append(text.substring(i + 2, close))
                builder.setSpan(StrikethroughSpan(), start, builder.length, 0)
                i = close + 2
                continue
            }
        }
        if (text[i] == '`') {
            val close = findClosingMarker(text, i + 1, "`")
            if (close != null) {
                val start = builder.length
                builder.append(text.substring(i + 1, close))
                builder.setSpan(TypefaceSpan("monospace"), start, builder.length, 0)
                i = close + 1
                continue
            }
        }
        if (text[i] == '*') {
            val close = findClosingMarker(text, i + 1, "*")
            if (close != null) {
                val start = builder.length
                builder.append(text.substring(i + 1, close))
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, builder.length, 0)
                i = close + 1
                continue
            }
        }
        builder.append(text[i])
        i++
    }
    return builder
}

// Una nota se considera vacía si no tiene título, ni texto, ni imágenes/videos/tareas.
fun isNoteEmpty(note: Note): Boolean {
    if (note.title.isNotBlank()) return false
    return when (note.type) {
        NoteType.TEXT -> {
            if (extractImageFileNames(note.content).isNotEmpty()) return false
            parseNoteContent(note.content).filterIsInstance<ContentPart.TextPart>().all { it.text.isBlank() }
        }
        NoteType.CHECKLIST -> note.checklistItems.all { it.text.isBlank() }
    }
}

// Vista de solo texto del contenido de una nota, sin imágenes/gifs/video (se
// reemplazan por un emoji indicativo). La usa el widget de "nota fijada": a
// diferencia de la UI normal, un widget de Glance no puede reusar
// parseNoteContent() para renderizar imágenes/galerías/video de verdad, así
// que esto le da al menos una idea de que ahí había contenido multimedia.
fun buildPlainTextPreview(note: Note): String = when (note.type) {
    NoteType.CHECKLIST -> note.checklistItems.joinToString("\n") { item ->
        (if (item.checked) "☑ " else "☐ ") + item.text
    }
    NoteType.TEXT -> parseNoteContent(note.content).joinToString("") { part ->
        when (part) {
            is ContentPart.TextPart -> part.text
            is ContentPart.ImagePart -> "📷 "
            is ContentPart.GalleryPart -> "🖼️ "
            is ContentPart.VideoPart -> "🎬 "
        }
    }.trim()
}

// Primer archivo de imagen/gif de la nota en orden real de aparición (ni
// video: para eso habría que decodificar un frame del archivo por
// separado, y no vale la pena para una miniatura de widget). null si la
// nota no tiene ninguna imagen o es de tipo checklist.
fun firstDisplayableImage(note: Note): String? =
    if (note.type == NoteType.TEXT) {
        extractMediaRefs(note.content).firstOrNull { !it.isVideo }?.fileName
    } else null

// Todos los nombres de archivo de imagen/gif referenciados en una lista ya
// parseada de ContentPart (sueltas + las de cada galería), en orden. Se usa
// para precargar de una las miniaturas que va a necesitar el widget antes
// de renderizar, en vez de ir cargando archivo por archivo durante el
// armado de la UI.
fun allInlineImageFileNames(parts: List<ContentPart>): List<String> =
    parts.flatMap { part ->
        when (part) {
            is ContentPart.ImagePart -> listOf(part.fileName)
            is ContentPart.GalleryPart -> part.fileNames
            else -> emptyList()
        }
    }
