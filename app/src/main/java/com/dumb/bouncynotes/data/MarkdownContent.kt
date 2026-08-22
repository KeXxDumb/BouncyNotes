package com.dumb.bouncynotes.data

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
    data class GalleryPart(val layout: GalleryLayout, val fileNames: List<String>) : ContentPart()
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

fun buildGalleryTag(layout: GalleryLayout, fileNames: List<String>): String =
    "[[gallery:${layout.name}:${fileNames.joinToString(",")}]]"

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
                val fileNames = match.groupValues[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (fileNames.isNotEmpty()) {
                    result.add(ContentPart.GalleryPart(parseGalleryLayout(match.groupValues[3]), fileNames))
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
            is ContentPart.GalleryPart -> part.fileNames.map { MediaRef(it, isVideo = false) }
            is ContentPart.VideoPart -> listOf(MediaRef(part.fileName, isVideo = true, caption = part.caption))
            is ContentPart.TextPart -> emptyList()
        }
    }

// occurrenceIndex se refiere a la posición de la imagen dentro de la lista plana
// que devuelve extractImageFileNames (0-based): cuenta imágenes sueltas y las
// que están dentro de un grupo por igual. Los videos no tienen descripción
// editable desde acá (no hace falta, no tienen un campo de caption editable
// en el editor todavía), por eso esta función sigue enfocada solo en imágenes.
fun updateImageCaption(content: String, occurrenceIndex: Int, newCaption: String): String {
    var count = -1
    return imageTagRegex.replace(content) { match ->
        count++
        if (count == occurrenceIndex) buildImageTag(match.groupValues[1], newCaption) else match.value
    }
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
                val fileNames = match.groupValues[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val remaining = fileNames.filterIndexed { i, _ -> flatIndex + i != occurrenceIndex }
                when {
                    remaining.isEmpty() -> { /* se quita el grupo entero */ }
                    // Un grupo con una sola imagen ya no es "grupo": lo dejamos
                    // como una imagen suelta normal en vez de una cuadrícula de 1.
                    remaining.size == 1 -> sb.append(buildImageTag(remaining[0]))
                    else -> sb.append(buildGalleryTag(parseGalleryLayout(layout), remaining))
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
