package com.example.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.notes.data.NoteImage
import java.io.File

@Composable
fun ImageAttachmentsSection(
    images: List<NoteImage>,
    onAddFromCamera: () -> Unit,
    onAddFromGallery: () -> Unit,
    onImageClick: (Int) -> Unit,
    onImageRemove: (Int) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Referencias e imágenes", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAddFromCamera) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = "Tomar foto")
            }
            IconButton(onClick = onAddFromGallery) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Elegir de galería")
            }
        }
        if (images.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(images.size) { index ->
                    val image = images[index]
                    Box(modifier = Modifier.size(96.dp)) {
                        AsyncImage(
                            model = File(image.path),
                            contentDescription = image.caption.ifBlank { "Imagen" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onImageClick(index) }
                        )
                        IconButton(
                            onClick = { onImageRemove(index) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Quitar imagen",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
