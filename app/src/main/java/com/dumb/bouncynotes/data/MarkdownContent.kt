package com.dumb.bouncynotes.data

// Sintaxis ligera al estilo Markdown para contenido embebido dentro del texto de una nota:
// [[img:nombre_de_archivo.jpg|descripción opcional]]           -> una sola imagen
// [[gallery:LAYOUT:archivo1.jpg,archivo2.jpg,archivo3.jpg]]    -> varias imágenes agrupadas
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
}

private val imageTagRegex = Regex("""\[\[img:([^|\]]+)\|([^\]]*)\]\]""")
private val galleryTagRegex = Regex("""\[\[gallery:([A-Z0-9_]+):([^\]]*)\]\]""")
// Combinada, para recorrer el contenido UNA sola vez y mantener el orden real
// de aparición de imágenes sueltas y grupos mezclados.
private val combinedTagRegex = Regex("""${imageTagRegex.pattern}|${galleryTagRegex.pattern}""")

fun buildImageTag(fileName: String, caption: String = ""): String =
    "[[img:$fileName|$caption]]"

fun buildGalleryTag(layout: GalleryLayout, fileNames: List<String>): String =
    "[[gallery:${layout.name}:${fileNames.joinToString(",")}]]"

private fun parseGalleryLayout(raw: String): GalleryLayout =
    runCatching { GalleryLayout.valueOf(raw) }.getOrDefault(GalleryLayout.GRID_2)

fun parseNoteContent(content: String): List<ContentPart> {
    val result = mutableListOf<ContentPart>()
    var lastIndex = 0
    for (match in combinedTagRegex.findAll(content)) {
        if (match.range.first > lastIndex) {
            result.add(ContentPart.TextPart(content.substring(lastIndex, match.range.first)))
        }
        // Grupos 1-2 vienen de imageTagRegex, 3-4 de galleryTagRegex (al estar
        // combinadas con "|" los números de grupo de la segunda regex quedan
        // corridos +2).
        if (match.value.startsWith("[[img:")) {
            result.add(
                ContentPart.ImagePart(
                    fileName = match.groupValues[1],
                    caption = match.groupValues[2]
                )
            )
        } else {
            val fileNames = match.groupValues[4].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (fileNames.isNotEmpty()) {
                result.add(ContentPart.GalleryPart(parseGalleryLayout(match.groupValues[3]), fileNames))
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < content.length) {
        result.add(ContentPart.TextPart(content.substring(lastIndex)))
    }
    return result
}

// Lista plana de TODOS los nombres de archivo de imagen de la nota, en orden
// real de aparición, incluyendo las que están dentro de un grupo (gallery).
// La usan tanto la limpieza de archivos al borrar la nota como el visor a
// pantalla completa (para poder pasar de una imagen a otra con swipe, sin
// importar si viene de una imagen suelta o de un grupo).
fun extractImageFileNames(content: String): List<String> =
    parseNoteContent(content).flatMap { part ->
        when (part) {
            is ContentPart.ImagePart -> listOf(part.fileName)
            is ContentPart.GalleryPart -> part.fileNames
            is ContentPart.TextPart -> emptyList()
        }
    }

// occurrenceIndex se refiere a la posición de la imagen dentro de la lista plana
// que devuelve extractImageFileNames (0-based): cuenta imágenes sueltas y las
// que están dentro de un grupo por igual.
fun updateImageCaption(content: String, occurrenceIndex: Int, newCaption: String): String {
    var count = -1
    return imageTagRegex.replace(content) { match ->
        count++
        if (count == occurrenceIndex) buildImageTag(match.groupValues[1], newCaption) else match.value
    }
}

// Quita UNA imagen de la lista plana (occurrenceIndex): si es una imagen
// suelta, quita el tag completo; si es una imagen dentro de un grupo, la
// saca solo a ella de la lista del grupo (y si era la última que quedaba,
// quita el grupo entero).
fun removeImageOccurrence(content: String, occurrenceIndex: Int): String {
    val sb = StringBuilder()
    var lastIndex = 0
    var flatIndex = 0
    for (match in combinedTagRegex.findAll(content)) {
        sb.append(content, lastIndex, match.range.first)
        val isImageTag = match.value.startsWith("[[img:")
        if (isImageTag) {
            if (flatIndex != occurrenceIndex) {
                sb.append(match.value)
            }
            flatIndex++
        } else {
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
        lastIndex = match.range.last + 1
    }
    sb.append(content, lastIndex, content.length)
    return sb.toString()
}

// Para previews en la lista: quita los marcadores de formato para que se lea limpio.
fun stripFormattingMarkers(text: String): String =
    text.replace("**", "").replace("~~", "").replace("`", "").replace("*", "")

// Una nota se considera vacía si no tiene título, ni texto, ni imágenes/tareas.
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
