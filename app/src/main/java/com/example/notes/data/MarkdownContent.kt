package com.example.notes.data

// Sintaxis ligera al estilo Markdown para imágenes embebidas dentro del texto de una nota:
// [[img:nombre_de_archivo.jpg|descripción opcional]]
// El archivo siempre vive en ImageStorage.imagesDir(context), así que solo se guarda el nombre.

sealed class ContentPart {
    data class TextPart(val text: String) : ContentPart()
    data class ImagePart(val fileName: String, val caption: String) : ContentPart()
}

private val imageTagRegex = Regex("""\[\[img:([^|\]]+)\|([^\]]*)\]\]""")

fun buildImageTag(fileName: String, caption: String = ""): String =
    "[[img:$fileName|$caption]]"

fun parseNoteContent(content: String): List<ContentPart> {
    val result = mutableListOf<ContentPart>()
    var lastIndex = 0
    for (match in imageTagRegex.findAll(content)) {
        if (match.range.first > lastIndex) {
            result.add(ContentPart.TextPart(content.substring(lastIndex, match.range.first)))
        }
        result.add(
            ContentPart.ImagePart(
                fileName = match.groupValues[1],
                caption = match.groupValues[2]
            )
        )
        lastIndex = match.range.last + 1
    }
    if (lastIndex < content.length) {
        result.add(ContentPart.TextPart(content.substring(lastIndex)))
    }
    return result
}

fun extractImageFileNames(content: String): List<String> =
    imageTagRegex.findAll(content).map { it.groupValues[1] }.toList()

// occurrenceIndex se refiere al orden de aparición de la imagen dentro del texto (0-based).
fun updateImageCaption(content: String, occurrenceIndex: Int, newCaption: String): String {
    var count = -1
    return imageTagRegex.replace(content) { match ->
        count++
        if (count == occurrenceIndex) buildImageTag(match.groupValues[1], newCaption) else match.value
    }
}

fun removeImageOccurrence(content: String, occurrenceIndex: Int): String {
    var count = -1
    return imageTagRegex.replace(content) { match ->
        count++
        if (count == occurrenceIndex) "" else match.value
    }
}

// Para previews en la lista: quita los marcadores de formato para que se lea limpio.
fun stripFormattingMarkers(text: String): String =
    text.replace("**", "").replace("~~", "").replace("`", "").replace("*", "")
