package com.dumb.bouncynotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.NoteImage
import com.dumb.bouncynotes.ui.components.NoteVideoPlayer
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    images: List<NoteImage>,
    startIndex: Int,
    onBack: () -> Unit,
    onDelete: (Int) -> Unit,
    // Pide el permiso de almacenamiento (solo hace falta en Android 9 o
    // anterior) y, una vez concedido (o si ya no hace falta), guarda el
    // archivo de esa posición en el dispositivo. Se resuelve así, en vez de
    // manejar el permiso acá adentro, porque pedirlo requiere un
    // rememberLauncherForActivityResult atado al ciclo de vida de la
    // Activity que aloja este composable.
    onSaveToDevice: (Int, (Boolean) -> Unit) -> Unit
) {
    if (images.isEmpty()) {
        onBack()
        return
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.size - 1)
    ) { images.size }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val thumbStripState = rememberLazyListState()

    // Mantiene la tira de miniaturas de abajo centrada en la página actual,
    // sin importar si el usuario llegó ahí con swipe, con las flechas, o
    // tocando directamente una miniatura.
    LaunchedEffect(pagerState.currentPage) {
        thumbStripState.animateScrollToItem((pagerState.currentPage - 2).coerceAtLeast(0))
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} / ${images.size}", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSaveToDevice(pagerState.currentPage) { ok ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (ok) "Guardado en el dispositivo" else "No se pudo guardar"
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "Guardar en el dispositivo", tint = Color.White)
                    }
                    IconButton(onClick = { onDelete(pagerState.currentPage) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val media = images[page]
                    if (media.isVideo) {
                        NoteVideoPlayer(
                            fileName = File(media.path).name,
                            modifier = Modifier.fillMaxSize(),
                            isActive = pagerState.currentPage == page
                        )
                    } else {
                        var scale by remember(page) { mutableStateOf(1f) }
                        var offsetX by remember(page) { mutableStateOf(0f) }
                        var offsetY by remember(page) { mutableStateOf(0f) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(page) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = File(media.path),
                                contentDescription = media.caption,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    )
                            )
                        }
                    }
                }

                // Flechas para pasar de imagen/video sin necesidad de deslizar
                // (útil en pantallas grandes o para quien prefiere tocar en vez
                // de arrastrar). Se ocultan en los extremos donde no aplican.
                if (pagerState.currentPage > 0) {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Anterior", tint = Color.White)
                    }
                }
                if (pagerState.currentPage < images.lastIndex) {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Siguiente", tint = Color.White)
                    }
                }
            }

            // El visor ahora es solo para ver/hacer zoom; editar la descripción se
            // hace desde el editor de la nota (botón "Descripción" bajo la imagen),
            // para no tener dos lugares distintos donde editar lo mismo.
            val current = images.getOrNull(pagerState.currentPage)
            if (!current?.caption.isNullOrBlank()) {
                Text(
                    text = current!!.caption,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Tira de miniaturas para saltar directo a cualquier imagen/video de
            // la nota sin tener que deslizar una por una. Solo tiene sentido
            // cuando hay más de un elemento.
            if (images.size > 1) {
                LazyRow(
                    state = thumbStripState,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    items(images.size) { index ->
                        val media = images[index]
                        val isSelected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (isSelected) Modifier.background(Color.White.copy(alpha = 0.3f)) else Modifier)
                                .padding(if (isSelected) 3.dp else 0.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        ) {
                            if (media.isVideo) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                AsyncImage(
                                    model = File(media.path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
