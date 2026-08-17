package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.parseNoteContent
import java.io.File

// Renderiza el contenido de una nota de texto en modo lectura: texto e imágenes
// intercalados exactamente en la posición donde el usuario los insertó.
@Composable
fun NoteContentView(content: String, onImageClick: (Int) -> Unit) {
    val context = LocalContext.current
    val parts = parseNoteContent(content)
    var imageOccurrence = 0

    Column {
        parts.forEach { part ->
            when (part) {
                is ContentPart.TextPart -> {
                    if (part.text.isNotBlank()) {
                        InlineMarkdownText(text = part.text)
                    }
                }
                is ContentPart.ImagePart -> {
                    val occurrenceIndex = imageOccurrence
                    imageOccurrence++
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        AsyncImage(
                            model = File(ImageStorage.imagesDir(context), part.fileName),
                            contentDescription = part.caption.ifBlank { "Imagen" },
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onImageClick(occurrenceIndex) }
                        )
                        if (part.caption.isNotBlank()) {
                            Text(
                                text = part.caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
