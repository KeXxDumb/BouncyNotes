package com.dumb.bouncynotes.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.withTimeoutOrNull
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.GalleryLayout
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteImage
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.buildGalleryTag
import com.dumb.bouncynotes.data.buildImageTag
import com.dumb.bouncynotes.data.buildVideoTag
import com.dumb.bouncynotes.data.extractImageFileNames
import com.dumb.bouncynotes.data.extractMediaRefs
import com.dumb.bouncynotes.data.MediaStorageExporter
import com.dumb.bouncynotes.data.isNoteEmpty
import com.dumb.bouncynotes.data.parseNoteContent
import com.dumb.bouncynotes.data.removeImageOccurrence
import com.dumb.bouncynotes.ui.components.ChecklistEditor
import com.dumb.bouncynotes.ui.components.CompactCaptionField
import com.dumb.bouncynotes.ui.components.FlatTextField
import com.dumb.bouncynotes.ui.components.GalleryGrid
import com.dumb.bouncynotes.ui.components.NoteContentView
import com.dumb.bouncynotes.ui.components.NoteVideoPlayer
import com.dumb.bouncynotes.ui.components.RgbColorPicker
import com.dumb.bouncynotes.ui.components.rememberImageAspectRatio
import java.io.File

// Modelo local para el editor segmentado: cada tramo de texto es un campo editable
// y cada imagen se renderiza de verdad (no como etiqueta de texto crudo).
private sealed class EditSegment {
    data class TextSeg(val value: TextFieldValue) : EditSegment()
    data class ImageSeg(val fileName: String, val caption: String) : EditSegment()
    data class GallerySeg(val layout: GalleryLayout, val fileNames: List<String>) : EditSegment()
    data class VideoSeg(val fileName: String, val caption: String) : EditSegment()
}

private fun buildEditSegments(content: String): List<EditSegment> {
    val parts = parseNoteContent(content)
    if (parts.isEmpty()) return listOf(EditSegment.TextSeg(TextFieldValue("")))
    // Si dos imágenes quedan una justo al lado de la otra en el contenido guardado
    // (sin ningún carácter entre medio), parseNoteContent no genera ningún TextPart
    // ahí porque literalmente no hay texto que representar. Eso hacía que, al volver
    // a abrir la nota, no existiera ningún campo editable entre esas imágenes y por
    // lo tanto fuera imposible escribir ahí. Insertamos un tramo de texto vacío
    // (solo en memoria, para editar) entre imágenes consecutivas, y al principio/final
    // si la nota empieza o termina con una imagen.
    val segments = mutableListOf<EditSegment>()
    fun isMediaSeg(seg: EditSegment) =
        seg is EditSegment.ImageSeg || seg is EditSegment.GallerySeg || seg is EditSegment.VideoSeg
    parts.forEach { part ->
        when (part) {
            is ContentPart.TextPart -> segments.add(EditSegment.TextSeg(TextFieldValue(part.text)))
            is ContentPart.ImagePart -> {
                if (segments.isEmpty() || isMediaSeg(segments.last())) {
                    segments.add(EditSegment.TextSeg(TextFieldValue("")))
                }
                segments.add(EditSegment.ImageSeg(part.fileName, part.caption))
            }
            is ContentPart.GalleryPart -> {
                if (segments.isEmpty() || isMediaSeg(segments.last())) {
                    segments.add(EditSegment.TextSeg(TextFieldValue("")))
                }
                segments.add(EditSegment.GallerySeg(part.layout, part.fileNames))
            }
            is ContentPart.VideoPart -> {
                if (segments.isEmpty() || isMediaSeg(segments.last())) {
                    segments.add(EditSegment.TextSeg(TextFieldValue("")))
                }
                segments.add(EditSegment.VideoSeg(part.fileName, part.caption))
            }
        }
    }
    if (isMediaSeg(segments.last())) {
        segments.add(EditSegment.TextSeg(TextFieldValue("")))
    }
    return segments
}

// El doble-tap para pasar a modo edición se hacía con detectTapGestures en el
// contenedor padre, en el pass por defecto (Main). Ese pass viaja de hijos hacia
// el padre, así que cuando el toque caía sobre un elemento hijo con su propia
// detección de toques (los enlaces del texto vía ClickableText, o antes las
// imágenes con .clickable), el hijo consumía el evento primero y el padre nunca
// llegaba a detectar el doble toque: por eso solo funcionaba tocando "fuera" del
// texto/imagen. Usando el pass Initial (que viaja de padre a hijos, antes de que
// cualquier hijo pueda consumir el evento) el contenedor ve el toque siempre.
private suspend fun PointerInputScope.detectDoubleTapToEdit(onDoubleTap: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial)
        waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(pass = PointerEventPass.Initial)
        } ?: return@awaitEachGesture
        secondDown.consume()
        waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
        onDoubleTap()
    }
}

private fun segmentsToContent(segments: List<EditSegment>): String =
    segments.joinToString("") { seg ->
        when (seg) {
            is EditSegment.TextSeg -> seg.value.text
            is EditSegment.ImageSeg -> buildImageTag(seg.fileName, seg.caption)
            is EditSegment.GallerySeg -> buildGalleryTag(seg.layout, seg.fileNames)
            is EditSegment.VideoSeg -> buildVideoTag(seg.fileName, seg.caption)
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    initialType: NoteType,
    viewModel: NoteViewModel,
    settings: AppSettings,
    biometricUnlockedForPrivate: Boolean,
    onRequestBiometric: (onSuccess: () -> Unit) -> Unit,
    allLabels: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var loaded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(Note(type = initialType)) }
    var segments by remember { mutableStateOf(listOf<EditSegment>(EditSegment.TextSeg(TextFieldValue("")))) }
    var activeSegmentIndex by remember { mutableStateOf(0) }
    var unlockedThisNote by remember { mutableStateOf(false) }
    var pendingCameraFileName by remember { mutableStateOf<String?>(null) }
    var showVideoTooLarge by remember { mutableStateOf(false) }
    // Guarda temporalmente qué archivo hay que exportar (y su callback de
    // resultado) mientras se espera la respuesta del diálogo de permiso de
    // almacenamiento (solo hace falta pedirlo en Android 9 o anterior).
    var pendingSaveToDevice by remember { mutableStateOf<Triple<String, Boolean, (Boolean) -> Unit>?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingSaveToDevice
        pendingSaveToDevice = null
        if (pending != null) {
            val (fileName, isVideo, callback) = pending
            if (granted) {
                val file = File(ImageStorage.imagesDir(context), fileName)
                callback(MediaStorageExporter.saveToDevice(context, file, isVideo))
            } else {
                callback(false)
            }
        }
    }
    var viewerStartPos by remember { mutableStateOf<Int?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    // Cuando se seleccionan varias imágenes a la vez desde la galería, quedan
    // acá mientras se le pregunta al usuario si van agrupadas o sueltas (ver
    // el AlertDialog más abajo). null = no hay ninguna pregunta pendiente.
    var pendingGroupFileNames by remember { mutableStateOf<List<String>?>(null) }
    var pendingGroupLayout by remember(pendingGroupFileNames) { mutableStateOf(settings.defaultGalleryLayout) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var captionActiveIndices by remember { mutableStateOf(setOf<Int>()) }
    var isEditing by remember { mutableStateOf(true) }

    // Destello de feedback al cambiar entre modo edición y modo vista: se
    // prende apenas isEditing cambia y se apaga solo, rápido, sin que el
    // usuario tenga que esperarlo (ver el AnimatedVisibility más abajo, y el
    // IconButton del ojo/lápiz que también dispara este cambio).
    //
    // La carga inicial de la nota también reasigna isEditing (ver
    // LaunchedEffect(noteId) más abajo, que lo fija según
    // settings.doubleTapToEdit) — eso NO es un cambio de modo real hecho por
    // el usuario, así que no debe destellar. Comparar contra el valor
    // anterior y exigir loaded=true evita ambos falsos positivos (el valor
    // inicial del remember de arriba, y el reajuste que hace la carga),
    // sin importar en qué orden terminen resolviéndose esos dos efectos.
    var showModeFlash by remember { mutableStateOf(false) }
    var previousIsEditingForFlash by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isEditing, loaded) {
        if (loaded && previousIsEditingForFlash != null && previousIsEditingForFlash != isEditing) {
            showModeFlash = true
            kotlinx.coroutines.delay(260)
            showModeFlash = false
        }
        previousIsEditingForFlash = isEditing
    }

    // BUG: al abrir el visor de imágenes a pantalla completa, más abajo hay un
    // "return" temprano que hace que todo el Scaffold con el contenido de la
    // nota (y su Column/LazyColumn con scroll) directamente NO se componga
    // mientras el visor está abierto. Al cerrar el visor, ese contenedor
    // vuelve a entrar en composición desde cero, y como su estado de scroll
    // se creaba ahí mismo (en la línea del .verticalScroll(...) / LazyColumn),
    // perdía cualquier scroll previo y arrancaba siempre en 0 (el inicio de
    // la nota). La solución es crear estos estados ACÁ arriba, antes de ese
    // "return": como esta parte de la función SIEMPRE se ejecuta en cada
    // recomposición (viewer abierto o no), Compose los recuerda de forma
    // estable sin importar que el contenedor de más abajo se deje de
    // componer temporalmente.
    val checklistScrollState = rememberScrollState()
    // LazyColumn en vez de un Column+verticalScroll: con notas de muchas
    // imágenes, esto evita que Coil cargue y mantenga en memoria TODAS las
    // imágenes a la vez — solo compone (y por lo tanto solo carga) los
    // segmentos cerca de lo que está en pantalla. rememberLazyListState() es
    // el equivalente de un ScrollState para LazyColumn; se hoistea acá por
    // la MISMA razón de arriba.
    val editLazyListState = rememberLazyListState()
    // Mismo cambio y mismo motivo que arriba, para el modo lectura (que es
    // donde más se sentía el delay con notas de varias imágenes, ya que es
    // el modo en el que más tiempo se pasa comparado con el de edición).
    val viewLazyListState = rememberLazyListState()

    // Para que el recordatorio realmente se vea, en Android 13+ hace falta el
    // permiso de notificaciones. Se pide justo al programar el primer
    // recordatorio, no al abrir la app (evita pedir permisos sin contexto).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* si lo niega, igual queda programada la alarma; solo no se verá la notificación */ }

    LaunchedEffect(noteId) {
        if (noteId != 0L) {
            viewModel.getById(noteId)?.let { current = it }
        }
        segments = buildEditSegments(current.content)
        isEditing = noteId == 0L || !settings.doubleTapToEdit
        loaded = true
    }

    fun updateContentFromSegments(newSegments: List<EditSegment>) {
        segments = newSegments
        current = current.copy(content = segmentsToContent(newSegments))
    }

    fun insertMediaSegmentAtActiveSegment(mediaSeg: EditSegment) {
        val idx = activeSegmentIndex.coerceIn(0, segments.size - 1)
        val seg = segments.getOrNull(idx)
        val newSegments = segments.toMutableList()
        // Índice del tramo de texto que queda después de insertar, para dejar el
        // cursor ahí. Antes activeSegmentIndex no se actualizaba tras insertar, así
        // que al elegir varias imágenes seguidas todas se insertaban en el mismo
        // punto original (el cursor de texto quedaba "congelado" al inicio de ese
        // tramo), y el texto existente terminaba empujado después de la 2ª imagen.
        var nextActiveIndex = idx
        if (seg is EditSegment.TextSeg) {
            val text = seg.value.text
            val cursor = seg.value.selection.start.coerceIn(0, text.length)
            val before = text.substring(0, cursor)
            val after = text.substring(cursor)

            // Línea actual (donde está el cursor), delimitada por saltos de línea.
            val lineStart = before.lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
            val lineEndRel = after.indexOf('\n').let { if (it == -1) after.length else it }
            val currentLine = before.substring(lineStart) + after.substring(0, lineEndRel)

            if (currentLine.isBlank()) {
                // La línea donde está el cursor está vacía: el medio (imagen o
                // grupo) la reemplaza directamente, sin dejar líneas vacías de sobra.
                val prefix = before.substring(0, lineStart).removeSuffix("\n")
                val suffix = after.substring(lineEndRel).removePrefix("\n")

                newSegments.removeAt(idx)
                var insertAt = idx
                if (prefix.isNotEmpty()) {
                    newSegments.add(insertAt, EditSegment.TextSeg(TextFieldValue(prefix)))
                    insertAt++
                } else if (newSegments.getOrNull(insertAt - 1).let { it is EditSegment.ImageSeg || it is EditSegment.GallerySeg }) {
                    // Si justo antes ya hay otra imagen/grupo (p. ej. se insertaron
                    // dos seguidos), dejamos un tramo de texto vacío como separador
                    // para que no queden pegados sin forma de escribir entre medio.
                    newSegments.add(insertAt, EditSegment.TextSeg(TextFieldValue("")))
                    insertAt++
                }
                newSegments.add(insertAt, mediaSeg)
                insertAt++
                // Siempre dejamos exactamente un tramo (con o sin texto) después del
                // medio para poder seguir escribiendo, nunca dos vacíos.
                newSegments.add(insertAt, EditSegment.TextSeg(TextFieldValue(suffix)))
                nextActiveIndex = insertAt
            } else {
                newSegments[idx] = EditSegment.TextSeg(TextFieldValue(before))
                newSegments.add(idx + 1, mediaSeg)
                newSegments.add(idx + 2, EditSegment.TextSeg(TextFieldValue(after)))
                nextActiveIndex = idx + 2
            }
        } else {
            newSegments.add(mediaSeg)
            newSegments.add(EditSegment.TextSeg(TextFieldValue("")))
            nextActiveIndex = newSegments.size - 1
        }
        activeSegmentIndex = nextActiveIndex
        updateContentFromSegments(newSegments)
    }

    fun insertImageAtActiveSegment(fileName: String) {
        insertMediaSegmentAtActiveSegment(EditSegment.ImageSeg(fileName, ""))
    }

    fun insertGalleryAtActiveSegment(fileNames: List<String>, layout: GalleryLayout) {
        insertMediaSegmentAtActiveSegment(EditSegment.GallerySeg(layout, fileNames))
    }

    fun insertVideoAtActiveSegment(fileName: String) {
        insertMediaSegmentAtActiveSegment(EditSegment.VideoSeg(fileName, ""))
    }

    fun deleteMediaSegment(idx: Int) {
        val seg = segments.getOrNull(idx) ?: return
        when (seg) {
            is EditSegment.ImageSeg -> ImageStorage.deleteFile(context, seg.fileName)
            is EditSegment.GallerySeg -> seg.fileNames.forEach { ImageStorage.deleteFile(context, it) }
            is EditSegment.VideoSeg -> ImageStorage.deleteFile(context, seg.fileName)
            is EditSegment.TextSeg -> return
        }
        val newSegments = segments.toMutableList()
        newSegments.removeAt(idx)
        if (idx > 0 && idx < newSegments.size) {
            val prev = newSegments[idx - 1]
            val next = newSegments[idx]
            if (prev is EditSegment.TextSeg && next is EditSegment.TextSeg) {
                newSegments[idx - 1] = EditSegment.TextSeg(TextFieldValue(prev.value.text + next.value.text))
                newSegments.removeAt(idx)
            }
        }
        if (newSegments.isEmpty()) newSegments.add(EditSegment.TextSeg(TextFieldValue("")))
        updateContentFromSegments(newSegments)
    }

    fun wrapActiveSelection(marker: String) {
        val idx = activeSegmentIndex.coerceIn(0, segments.size - 1)
        val seg = segments.getOrNull(idx) as? EditSegment.TextSeg ?: return
        val text = seg.value.text
        val sel = seg.value.selection
        val start = sel.start.coerceIn(0, text.length)
        val end = sel.end.coerceIn(0, text.length)
        val selected = text.substring(start, end)
        val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
        val newSelection = if (start == end) {
            TextRange(start + marker.length)
        } else {
            TextRange(start + marker.length, end + marker.length)
        }
        val newSegments = segments.toMutableList()
        newSegments[idx] = EditSegment.TextSeg(TextFieldValue(newText, newSelection))
        updateContentFromSegments(newSegments)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        val fileNames = uris.mapNotNull { uri ->
            if (settings.compressImages) {
                ImageStorage.compressFromUri(context, uri, settings.imageQuality)
            } else {
                ImageStorage.copyFromUri(context, uri)
            }
        }
        when {
            fileNames.isEmpty() -> { /* el usuario canceló o algo falló al copiar */ }
            fileNames.size == 1 -> {
                // Con una sola imagen no hay nada que preguntar: se inserta
                // directo, como siempre.
                insertImageAtActiveSegment(fileNames.first())
                focusManager.clearFocus(force = true)
            }
            else -> {
                // Varias imágenes de una: en vez de insertarlas ya mismo una
                // tras otra (como hacía antes), preguntamos si van agrupadas
                // o sueltas.
                pendingGroupFileNames = fileNames
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val fileName = pendingCameraFileName
        if (success && fileName != null) {
            if (settings.compressImages) ImageStorage.compressInPlace(context, fileName, settings.imageQuality)
            insertImageAtActiveSegment(fileName)
            focusManager.clearFocus(force = true)
        } else if (fileName != null) {
            ImageStorage.deleteFile(context, fileName)
        }
        pendingCameraFileName = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = ImageStorage.createCaptureFile(context)
            pendingCameraFileName = file.name
            cameraLauncher.launch(uri)
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = ImageStorage.copyVideoFromUri(context, uri)
            when {
                result.fileName != null -> {
                    insertVideoAtActiveSegment(result.fileName)
                    focusManager.clearFocus(force = true)
                }
                result.tooLarge -> showVideoTooLarge = true
            }
        }
    }

    // Sin esto, salir con el gesto/botón de retroceso del sistema (en vez de la
    // flecha propia de la app) descartaba cualquier cambio sin guardar, incluido
    // fijar/desfijar la nota.
    BackHandler(enabled = loaded && viewerStartPos != null) {
        viewerStartPos = null
    }
    BackHandler(
        enabled = loaded && viewerStartPos == null &&
            !(current.isPrivate && !biometricUnlockedForPrivate && !unlockedThisNote)
    ) {
        if (!isNoteEmpty(current)) viewModel.save(current)
        onBack()
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val isLockedForMe = current.isPrivate && !biometricUnlockedForPrivate && !unlockedThisNote

    if (isLockedForMe) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nota privada") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Esta nota es privada")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onRequestBiometric { unlockedThisNote = true } }) {
                        Text("Desbloquear")
                    }
                }
            }
        }
        return
    }

    fun applyViewerContentChange(newContent: String) {
        current = current.copy(content = newContent)
        if (isEditing) segments = buildEditSegments(newContent)
    }

    if (viewerStartPos != null) {
        val fileNames = extractImageFileNames(current.content)
        // Antes esto se armaba a mano (filtrando ContentPart.ImagePart y
        // buscando captions aparte), lo cual además de repetir lógica que ya
        // vive en extractMediaRefs, no sabía distinguir cuáles archivos eran
        // video (por eso el visor siempre intentaba mostrarlos como imagen).
        // extractMediaRefs ya devuelve, en el mismo orden plano que usa
        // removeImageOccurrence, si cada ítem es imagen o video.
        val mediaRefs = extractMediaRefs(current.content)
        val images = mediaRefs.map { ref ->
            NoteImage(
                path = "${ImageStorage.imagesDir(context)}/${ref.fileName}",
                caption = ref.caption,
                isVideo = ref.isVideo
            )
        }
        ImageViewerScreen(
            images = images,
            startIndex = viewerStartPos ?: 0,
            onBack = { viewerStartPos = null },
            // Solo se puede borrar una imagen desde acá si el visor se abrió
            // en modo edición. Antes esto no se chequeaba: tocar una imagen
            // en modo VISTA para verla más grande abría el mismo visor con
            // el botón de borrar siempre visible, permitiendo borrarla sin
            // haber entrado a editar la nota para nada.
            canDelete = isEditing,
            onDelete = { pos ->
                if (pos < fileNames.size) ImageStorage.deleteFile(context, fileNames[pos])
                applyViewerContentChange(removeImageOccurrence(current.content, pos))
                viewerStartPos = null
            },
            onSaveToDevice = { pos, callback ->
                val ref = mediaRefs.getOrNull(pos)
                if (ref == null) {
                    callback(false)
                } else {
                    pendingSaveToDevice = Triple(ref.fileName, ref.isVideo, callback)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val file = File(ImageStorage.imagesDir(context), ref.fileName)
                        val ok = MediaStorageExporter.saveToDevice(context, file, ref.isVideo)
                        pendingSaveToDevice = null
                        callback(ok)
                    } else {
                        storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }
        )
        return
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Agregar contenido") },
            text = {
                Column {
                    Text("Se insertará donde está el cursor.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        Triple(Icons.Filled.PhotoCamera, "Cámara") {
                            showImageSourceDialog = false
                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                        },
                        Triple(Icons.Filled.PhotoLibrary, "Galería (fotos y gifs)") {
                            showImageSourceDialog = false
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        Triple(Icons.Filled.Videocam, "Video (máx. ${ImageStorage.MAX_VIDEO_BYTES / (1024 * 1024)} MB)") {
                            showImageSourceDialog = false
                            videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    ).forEach { (icon, label, onClick) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showVideoTooLarge) {
        AlertDialog(
            onDismissRequest = { showVideoTooLarge = false },
            title = { Text("Video demasiado pesado") },
            text = {
                Text(
                    "Este video pesa más de ${ImageStorage.MAX_VIDEO_BYTES / (1024 * 1024)} MB, " +
                        "así que no se agregó a la nota. Los videos no se comprimen (recodificar " +
                        "video en el teléfono es lento), por eso hay un límite de tamaño de origen."
                )
            },
            confirmButton = {
                TextButton(onClick = { showVideoTooLarge = false }) { Text("Entendido") }
            }
        )
    }

    if (pendingGroupFileNames != null) {
        val names = pendingGroupFileNames.orEmpty()
        AlertDialog(
            onDismissRequest = {
                // Si cierran el diálogo tocando afuera, insertamos sueltas (el
                // comportamiento de siempre) en vez de perder las imágenes que
                // ya se copiaron al almacenamiento de la app.
                names.forEach { insertImageAtActiveSegment(it) }
                pendingGroupFileNames = null
                focusManager.clearFocus(force = true)
            },
            title = { Text("${names.size} imágenes seleccionadas") },
            text = {
                Column {
                    Text("¿Las agregamos como un grupo o cada una por separado?")
                    Spacer(Modifier.height(12.dp))
                    Text("Formato del grupo", style = MaterialTheme.typography.labelMedium)
                    GalleryLayout.entries.forEach { layout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingGroupLayout = layout }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pendingGroupLayout == layout,
                                onClick = { pendingGroupLayout = layout }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(layout.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    insertGalleryAtActiveSegment(names, pendingGroupLayout)
                    pendingGroupFileNames = null
                    focusManager.clearFocus(force = true)
                }) { Text("Agrupadas") }
            },
            dismissButton = {
                TextButton(onClick = {
                    names.forEach { insertImageAtActiveSegment(it) }
                    pendingGroupFileNames = null
                    focusManager.clearFocus(force = true)
                }) { Text("Sueltas") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (settings.useTrash) "¿Enviar a la papelera?" else "¿Eliminar nota?") },
            text = {
                Text(
                    if (settings.useTrash) "Podrás restaurarla después desde la Papelera."
                    else "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (settings.useTrash) {
                        viewModel.moveToTrash(current)
                    } else {
                        extractImageFileNames(current.content).forEach { ImageStorage.deleteFile(context, it) }
                        viewModel.deleteForever(current)
                    }
                    onBack()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Color", style = MaterialTheme.typography.labelLarge)
                RgbColorPicker(
                    selectedHex = current.color,
                    onColorChange = { current = current.copy(color = it) }
                )
                Spacer(Modifier.height(12.dp))
                LabelsEditor(
                    selectedLabels = current.labels,
                    allLabels = allLabels,
                    onLabelsChange = { current = current.copy(labels = it) }
                )
            }
        }
    }

    if (showReminderSheet) {
        ReminderPickerSheet(
            initialMillis = current.reminderAt,
            initialDays = current.reminderDays,
            initialCalendarDates = current.reminderCalendarDates,
            initialCalendarRecurring = current.reminderCalendarRecurring,
            onDismiss = { showReminderSheet = false },
            onConfirm = { millis, days, calendarDates, calendarRecurring ->
                // Antes esto solo tocaba current.reminderAt en memoria: el
                // guardado real (y con él, ReminderScheduler.schedule) recién
                // pasaba al salir de la pantalla con la flecha/back. Si el
                // usuario confirmaba el recordatorio y después salía por
                // cualquier otro camino (botón Home, cambiar de app y que
                // Android mate el proceso, etc.) sin volver a tocar la
                // flecha, el recordatorio quedaba "puesto" en la UI que veía
                // el usuario, pero nunca llegaba a programarse a nivel de
                // AlarmManager. Guardar acá mismo, apenas se confirma,
                // asegura que quede en la base de datos y programado de una,
                // sin depender de cómo el usuario termine saliendo de la
                // pantalla.
                val updated = current.copy(
                    reminderAt = millis,
                    reminderDays = days,
                    reminderCalendarDates = calendarDates,
                    reminderCalendarRecurring = calendarRecurring
                )
                current = updated
                viewModel.save(updated) { id ->
                    if (noteId == 0L) current = current.copy(id = id)
                }
                showReminderSheet = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onClear = {
                // Mismo motivo que en onConfirm: si no se guarda ahora, un
                // recordatorio que el usuario acaba de "borrar" en la UI
                // puede seguir sonando igual porque la cancelación real
                // (ReminderScheduler.cancel) nunca llegó a ejecutarse.
                val updated = current.copy(
                    reminderAt = null,
                    reminderDays = emptySet(),
                    reminderCalendarDates = emptySet(),
                    reminderCalendarRecurring = false
                )
                current = updated
                viewModel.save(updated) { id ->
                    if (noteId == 0L) current = current.copy(id = id)
                }
                showReminderSheet = false
            }
        )
    }


    val contentLayer = rememberGraphicsLayer()
    val bottomBarHeight = 56.dp
    // El Spacer de compensación al final del contenido necesita el alto TOTAL
    // que ocupa la barra en pantalla, no solo sus 56dp fijos: más abajo, el
    // Box que envuelve a GlassBottomBar la empuja hacia arriba con
    // navigationBarsPadding() en los teléfonos donde el sistema no la
    // acomoda solo (ver comentario ahí — bug ya visto una vez en otro
    // dispositivo). Si el Spacer no suma ESE MISMO inset, en esos teléfonos
    // la barra ocupa más alto real del que el Spacer previó, y la última
    // línea del contenido queda tapada justo esa diferencia — es lo que
    // reportó el amigo del usuario.
    val bottomBarNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarCompensation = bottomBarHeight + bottomBarNavInset + 12.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isNoteEmpty(current)) viewModel.save(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            // Antes era un BottomAppBar de Material3: ese componente reserva
            // ~80dp de alto con relleno pensado para llevar un FAB embebido, que
            // acá no usamos, así que sobraba una franja enorme vacía. Ahora es
            // un contenedor propio con una altura fija y compacta (56dp, la
            // misma que un TopAppBar chico) que además es semitransparente y
            // desenfoca lo que hay detrás (el contenido de la nota, que se deja
            // scrollear por debajo de la barra) para un efecto "vidrio
            // esmerilado" en vez de un panel sólido.
            //
            // BUG reportado por un usuario en otro dispositivo (a mí no me
            // pasaba): la barra terminaba DETRÁS de la barra de navegación
            // del sistema en vez de arriba. Causa: con targetSdk 36
            // (Android 15+), el sistema fuerza edge-to-edge sin importar lo
            // que haga la app — el contenido puede dibujarse por detrás de
            // las barras del sistema, y ya no hay un "acomodo automático"
            // como en versiones viejas de Android. GlassBottomBar es una Box
            // propia con altura FIJA que nunca pedía el inset de la barra de
            // navegación, así que en un teléfono con edge-to-edge forzado
            // quedaba tapada por ella. navigationBarsPadding() en un Box que
            // ENVUELVE a GlassBottomBar (no adentro de ella, que le comería
            // altura útil a los botones) empuja toda la barra hacia arriba
            // lo que haga falta; en un teléfono donde el sistema ya la
            // acomodaba solo, ese padding termina siendo 0 y no cambia nada.
            Box(modifier = Modifier.navigationBarsPadding()) {
                GlassBottomBar(
                    contentLayer = contentLayer,
                    height = bottomBarHeight,
                    centered = isEditing
                ) {
                if (current.deletedAt != null) {
                    IconButton(onClick = {
                        extractImageFileNames(current.content).forEach { ImageStorage.deleteFile(context, it) }
                        viewModel.deleteForever(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = "Eliminar para siempre")
                    }
                    IconButton(onClick = {
                        viewModel.restore(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Restaurar")
                    }
                } else {
                    IconButton(onClick = {
                        if (settings.confirmBeforeDelete) {
                            showDeleteConfirm = true
                        } else if (settings.useTrash) {
                            viewModel.moveToTrash(current)
                            onBack()
                        } else {
                            extractImageFileNames(current.content).forEach { ImageStorage.deleteFile(context, it) }
                            viewModel.deleteForever(current)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar")
                    }
                }
                if (current.type == NoteType.TEXT && isEditing) {
                    IconButton(onClick = { showImageSourceDialog = true }) {
                        Icon(Icons.Filled.Image, contentDescription = "Insertar imagen")
                    }
                    IconButton(onClick = { wrapActiveSelection("**") }) {
                        Icon(Icons.Filled.FormatBold, contentDescription = "Negrita")
                    }
                    IconButton(onClick = { wrapActiveSelection("*") }) {
                        Icon(Icons.Filled.FormatItalic, contentDescription = "Cursiva")
                    }
                    IconButton(onClick = { wrapActiveSelection("~~") }) {
                        Icon(Icons.Filled.FormatStrikethrough, contentDescription = "Tachado")
                    }
                    IconButton(onClick = { wrapActiveSelection("`") }) {
                        Icon(Icons.Filled.Code, contentDescription = "Monoespaciado")
                    }
                }
                if (isEditing) {
                    IconButton(onClick = { showMoreSheet = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "Color y etiquetas")
                    }
                    IconButton(onClick = { showReminderSheet = true }) {
                        Icon(
                            if (current.reminderAt != null) Icons.Filled.Alarm else Icons.Outlined.AlarmAdd,
                            contentDescription = "Recordatorio",
                            tint = if (current.reminderAt != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                } else {
                    // Antes vivían en la barra de arriba; ahora están acá abajo
                    // junto con el resto de acciones sobre la nota ya guardada
                    // (siguen apareciendo solo en modo vista, no en edición).
                    IconButton(onClick = { current = current.copy(pinned = !current.pinned) }) {
                        Icon(
                            if (current.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Fijar"
                        )
                    }
                    IconButton(onClick = {
                        if (current.isPrivate) {
                            current = current.copy(isPrivate = false)
                        } else {
                            onRequestBiometric {
                                current = current.copy(isPrivate = true)
                                unlockedThisNote = true
                            }
                        }
                    }) {
                        Icon(
                            if (current.isPrivate) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                            contentDescription = "Privada"
                        )
                    }
                }
                // Antes este botón vivía en la barra de arriba y solo aparecía para
                // notas de texto: las notas de tipo checklist no tenían forma de
                // volver a modo edición una vez guardadas (con "doble toque para
                // editar" activado, quedaban bloqueadas en solo lectura para
                // siempre). Ahora vive abajo, junto al resto de acciones, y
                // funciona para ambos tipos de nota.
                //
                // El botón de guardar (✓) que estaba acá al lado se quitó: tanto
                // este mismo botón del ojo (al pasar a modo vista) como la flecha
                // de volver ya guardan la nota si hubo cambios, así que era
                // redundante.
                IconButton(onClick = {
                    val goingToEdit = !isEditing
                    if (goingToEdit && current.type == NoteType.TEXT) {
                        segments = buildEditSegments(current.content)
                    } else if (!goingToEdit) {
                        // Al pasar de edición a vista es cuando efectivamente
                        // "se sale" del modo de edición, así que aprovechamos
                        // para guardar acá (antes solo se guardaba al tocar el
                        // botón de guardar dedicado, o al salir con la flecha).
                        if (!isNoteEmpty(current)) {
                            viewModel.save(current) { id ->
                                if (noteId == 0L) current = current.copy(id = id)
                            }
                        }
                    }
                    isEditing = goingToEdit
                }) {
                    // Un pequeño AnimatedContent para que el propio ícono
                    // también "avise" el cambio de modo (gira/rota levemente
                    // en vez de cambiar de golpe), reforzando el fundido del
                    // contenido de más abajo.
                    AnimatedContent(
                        targetState = isEditing,
                        transitionSpec = {
                            (scaleIn(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)))
                                .togetherWith(scaleOut(animationSpec = tween(120)) + fadeOut(animationSpec = tween(120)))
                        },
                        label = "icono-modo-edicion-vista"
                    ) { editing ->
                        Icon(
                            if (editing) Icons.Filled.RemoveRedEye else Icons.Outlined.EditNote,
                            contentDescription = if (editing) "Vista previa" else "Editar"
                        )
                    }
                }
            }
            }
        }
    ) { padding ->
        val readModeGesture = if (!isEditing) {
            Modifier.pointerInput(Unit) {
                detectDoubleTapToEdit {
                    segments = buildEditSegments(current.content)
                    isEditing = true
                }
            }
        } else Modifier

        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // OJO: a propósito NO aplicamos el padding inferior que da el
                // Scaffold acá. Si lo hiciéramos, el contenido nunca se dibujaría
                // detrás de la barra inferior y no habría nada que desenfocar
                // (desenfocar "nada" no se nota). En su lugar dejamos que el
                // contenido llegue hasta el fondo real de la pantalla, y más abajo
                // le agregamos un espacio en blanco del alto de la barra para que
                // el texto/checklist no quede tapado al hacer scroll hasta el final.
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current)
                )
                .padding(horizontal = 12.dp)
                // Graba todo lo que se dibuja acá (título, texto, checklist,
                // imágenes) en una "capa" que la barra de abajo puede volver a
                // dibujar recortada y desenfocada, logrando el efecto de vidrio
                // esmerilado sin duplicar la UI real.
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                }
        ) {
            if (isEditing) {
                FlatTextField(
                    value = current.title,
                    onValueChange = { current = current.copy(title = it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Título") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            } else if (current.title.isNotBlank()) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (current.type == NoteType.CHECKLIST) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // .imePadding() ANTES de .verticalScroll(), no
                            // después: en ese orden, el padding ACHICA la
                            // caja disponible para el scroll (el viewport
                            // real), en vez de sumarse como espacio extra
                            // DENTRO del contenido scrolleable — que es lo
                            // que pasaba antes, y por lo que el cálculo de
                            // "hasta dónde hay que scrollear" para que el
                            // cursor quede visible no daba bien: Compose no
                            // se enteraba de que el teclado había reducido
                            // el alto disponible de verdad. Es un no-op
                            // (0dp) cuando el teclado está oculto, así que
                            // no afecta el modo lectura de la checklist.
                            .imePadding()
                            .verticalScroll(checklistScrollState)
                            .then(readModeGesture)
                    ) {
                        ChecklistEditor(
                            items = current.checklistItems,
                            checkboxPosition = settings.checkboxPosition,
                            readOnly = !isEditing,
                            onItemsChange = { newItems ->
                                val finalItems = if (settings.autoSortChecked) {
                                    newItems.sortedBy { it.checked }
                                } else newItems
                                current = current.copy(checklistItems = finalItems)
                            }
                        )
                        // Despeje para que el último ítem no quede tapado por la
                        // barra inferior flotante y semitransparente.
                        Spacer(Modifier.height(bottomBarCompensation))
                    }
                } else {
                    // Antes el cambio entre modo edición y vista (el botón del
                    // ojo/lápiz en la barra inferior) era un salto instantáneo,
                    // sin ninguna transición, así que costaba notar que
                    // realmente había cambiado de modo. Crossfade anima un
                    // fundido cruzado entre ambos sin tocar el scroll de cada
                    // uno (que ya quedan hoisted arriba, en
                    // editLazyListState/viewLazyListState).
                    Crossfade(
                        targetState = isEditing,
                        animationSpec = tween(220),
                        label = "modo-edicion-vista"
                    ) { editing ->
                        if (editing) {
                    LazyColumn(
                        state = editLazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            // Mismo motivo que antes con verticalScroll: el
                            // padding del teclado tiene que achicar el
                            // viewport real de la LazyColumn (pasado acá,
                            // en su propio Modifier), no sumarse como
                            // espacio aparte dentro del contenido.
                            .imePadding()
                    ) {
                        itemsIndexed(segments, key = { index, _ -> index }) { index, segment ->
                            when (segment) {
                                is EditSegment.TextSeg -> {
                                    // Solo el campo activo necesita pedir "traeme a la
                                    // vista" (no tiene sentido, y sería más costoso,
                                    // hacerlo para todos los tramos de texto de la nota).
                                    val bringIntoViewRequester = if (index == activeSegmentIndex) {
                                        remember(index) { BringIntoViewRequester() }
                                    } else null
                                    FlatTextField(
                                        value = segment.value,
                                        onValueChange = { value ->
                                            val newSegments = segments.toMutableList()
                                            newSegments[index] = EditSegment.TextSeg(value)
                                            updateContentFromSegments(newSegments)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (segments.size == 1) Modifier.heightIn(min = 560.dp)
                                                else Modifier
                                            )
                                            .onFocusChanged { if (it.isFocused) activeSegmentIndex = index },
                                        placeholder = { Text("Escribe...") },
                                        bringIntoViewRequester = bringIntoViewRequester
                                    )
                                }
                                is EditSegment.ImageSeg -> {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Box {
                                            // Cuenta imágenes en la lista PLANA (sueltas + las
                                            // que están dentro de cada grupo anterior), la misma
                                            // indexación que usa extractImageFileNames y por lo
                                            // tanto el visor a pantalla completa.
                                            val imageIndex = segments.take(index).sumOf { s ->
                                                when (s) {
                                                    is EditSegment.ImageSeg -> 1
                                                    is EditSegment.GallerySeg -> s.fileNames.size
                                                    is EditSegment.VideoSeg -> 1
                                                    is EditSegment.TextSeg -> 0
                                                }
                                            }
                                            // Se usa la relación de aspecto REAL de la imagen
                                            // (ver rememberImageAspectRatio) para que LazyColumn
                                            // sepa el alto correcto ANTES de que Coil termine de
                                            // cargarla del todo, sin tener que recortarla a una
                                            // proporción fija.
                                            val imageAspectRatio = rememberImageAspectRatio(context, segment.fileName)
                                            AsyncImage(
                                                model = File(ImageStorage.imagesDir(context), segment.fileName),
                                                contentDescription = null,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(imageAspectRatio)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { viewerStartPos = imageIndex }
                                            )
                                            IconButton(
                                                onClick = { deleteMediaSegment(index) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(28.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Quitar imagen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            if (segment.caption.isBlank() && index !in captionActiveIndices) {
                                                TextButton(
                                                    onClick = { captionActiveIndices = captionActiveIndices + index },
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    colors = ButtonDefaults.textButtonColors(
                                                        containerColor = Color.Black.copy(alpha = 0.5f),
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Descripción", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                        if (segment.caption.isNotBlank() || index in captionActiveIndices) {
                                            CompactCaptionField(
                                                value = segment.caption,
                                                onValueChange = { caption ->
                                                    val newSegments = segments.toMutableList()
                                                    newSegments[index] = segment.copy(caption = caption)
                                                    updateContentFromSegments(newSegments)
                                                },
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                is EditSegment.GallerySeg -> {
                                    // Índice plano de la primera imagen de este grupo (misma
                                    // lógica que en la rama de arriba).
                                    val startIndex = segments.take(index).sumOf { s ->
                                        when (s) {
                                            is EditSegment.ImageSeg -> 1
                                            is EditSegment.GallerySeg -> s.fileNames.size
                                            is EditSegment.VideoSeg -> 1
                                            is EditSegment.TextSeg -> 0
                                        }
                                    }
                                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                        // Antes esto llamaba a GalleryEditorPreview, un preview
                                        // "genérico" (siempre una fila de miniaturas) que NO
                                        // reflejaba el formato elegido (cuadrícula 2/3, carrusel).
                                        // Por eso, sin importar qué formato se eligiera en el
                                        // popup, en el editor siempre se veía igual (una fila, que
                                        // se confundía con "siempre carrusel"). Ahora reutiliza el
                                        // mismo GalleryGrid que se usa en modo lectura, así el
                                        // editor muestra exactamente el layout real elegido.
                                        GalleryGrid(
                                            layout = segment.layout,
                                            fileNames = segment.fileNames,
                                            onImageClick = { i -> viewerStartPos = startIndex + i }
                                        )
                                        // A diferencia de una imagen suelta, acá no se puede
                                        // sacar una sola imagen del grupo desde el editor (para
                                        // eso está el visor a pantalla completa, que sí borra de
                                        // a una); este botón quita el grupo entero.
                                        IconButton(
                                            onClick = { deleteMediaSegment(index) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(28.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Quitar grupo de imágenes",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                is EditSegment.VideoSeg -> {
                                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                        NoteVideoPlayer(
                                            fileName = segment.fileName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                        IconButton(
                                            onClick = { deleteMediaSegment(index) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(28.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Quitar video",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "bottom-bar-spacer") {
                            Spacer(Modifier.height(bottomBarCompensation))
                        }
                    }
                        } else {
                    LazyColumn(
                        state = viewLazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(readModeGesture)
                    ) {
                        NoteContentView(
                            context = context,
                            content = current.content,
                            onImageClick = { idx -> viewerStartPos = idx }
                        )
                        item(key = "bottom-bar-spacer-view") {
                            Spacer(Modifier.height(bottomBarCompensation))
                        }
                    }
                        }
                    }
                }
            }
            // Cierra el Column original (título + contenido) que envuelve
            // todo lo de arriba: el destello de abajo tiene que ser HERMANO
            // de ese Column (ambos hijos directos del Box nuevo), no quedar
            // anidado adentro de él.
            }

            // Antes el único indicio de que se había cambiado de modo edición/vista
            // era el Crossfade del contenido y la pequeña animación del ícono del
            // botón — ambos sutiles y fáciles de no notar, sobre todo porque pasan
            // "adentro" de la barra inferior en vez de ocupar la pantalla. Este
            // destello es mucho más notorio a propósito: el ícono del modo al que
            // se acaba de entrar, grande, con el fondo oscurecido detrás SOLO
            // mientras dura el destello (no es un overlay que bloquee nada), y
            // rápido (no debe sentirse como una carga: aparece y se va solo, sin
            // que el usuario tenga que esperarlo ni tocar nada).
            AnimatedVisibility(
                visible = showModeFlash,
                // Antes entraba en 90ms, muy repentino comparado con lo
                // suave que ya se sentía el fadeOut (200ms) — se pareja a
                // una duración similar, para que aparecer y desaparecer se
                // sientan igual de naturales.
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isEditing) Icons.Outlined.EditNote else Icons.Filled.RemoveRedEye,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsEditor(
    selectedLabels: List<String>,
    allLabels: List<String>,
    onLabelsChange: (List<String>) -> Unit
) {
    var newLabel by remember { mutableStateOf("") }
    Column {
        Text("Etiquetas", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val combined = (allLabels + selectedLabels).distinct()
            combined.forEach { label ->
                val selected = label in selectedLabels
                FilterChip(
                    selected = selected,
                    onClick = {
                        onLabelsChange(if (selected) selectedLabels - label else selectedLabels + label)
                    },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlatTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nueva etiqueta") },
                singleLine = true
            )
            TextButton(onClick = {
                if (newLabel.isNotBlank() && newLabel !in selectedLabels) {
                    onLabelsChange(selectedLabels + newLabel.trim())
                    newLabel = ""
                }
            }) { Text("Agregar") }
        }
    }
}

// Barra inferior "de vidrio esmerilado" para el editor de notas.
//
// Reemplaza al BottomAppBar de Material3 (que reservaba ~80dp con relleno
// pensado para un FAB embebido que acá no se usa). Esta versión:
//  - tiene una altura fija y compacta (parámetro `height`, 56dp desde donde se
//    llama), en vez de la altura excesiva por defecto.
//  - centra sus botones cuando `centered = true` (modo edición); en modo
//    vista los deja alineados al inicio, ya que ahí hay menos botones y
//    centrarlos se vería raro con tanto espacio vacío alrededor.
//  - es semitransparente y desenfoca lo que hay detrás en vez de tapar todo
//    con un panel sólido. El contenido de la nota (en NoteEditScreen) se
//    deja dibujar por debajo de esta barra a propósito, grabando su dibujo
//    en `contentLayer`; acá simplemente volvemos a dibujar (recortada a esta
//    franja) esa misma grabación con un desenfoque real encima, así que lo
//    que se ve "a través" de la barra es efectivamente el contenido real que
//    hay detrás, no una imitación.
@Composable
private fun GlassBottomBar(
    contentLayer: GraphicsLayer,
    height: Dp,
    centered: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    // Antes el tinte de la barra era MaterialTheme.colorScheme.surface, que
    // en tema claro es un color CLARO: con eso, más el desenfoque, los
    // íconos (que también toman un tono relativamente oscuro/neutro del
    // tema) quedaban con muy poco contraste encima y costaba distinguirlos.
    // Ahora el tinte es directamente oscuro siempre (sin importar el tema
    // claro/oscuro de la app), y los íconos se fuerzan a un blanco casi
    // puro con CompositionLocalProvider más abajo, así el contraste es
    // consistente sin importar qué tan clara sea la nota o el tema elegido.
    val barTint = Color.Black
    val barShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // Borde sutil arriba y a los costados para que la barra se
            // distinga claramente del contenido que se ve (desenfocado)
            // detrás de ella, en vez de mezclarse con él.
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.18f), shape = barShape)
    ) {
        // Capa de fondo: copia recortada y desenfocada del contenido de la
        // nota que queda "detrás" de esta barra, más un tinte oscuro
        // semitransparente.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(barShape)
                .graphicsLayer {
                    clip = true
                    // El desenfoque real (RenderEffect) solo existe desde
                    // Android 12 (API 31). En versiones anteriores nos
                    // quedamos con la transparencia sola: se sigue viendo
                    // "liviana" aunque sin el desenfoque, degradación
                    // razonable en vez de romper algo.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(26f, 26f, TileMode.Clamp)
                    }
                }
                .drawWithContent {
                    val layerSize = contentLayer.size
                    if (layerSize.height > 0) {
                        // El contenido grabado empieza en la parte de arriba de
                        // la pantalla; a esta barra le corresponde solo su
                        // franja final (la más cercana al borde inferior), así
                        // que lo trasladamos hacia arriba para recortar
                        // justo esa porción.
                        translate(top = -(layerSize.height - size.height)) {
                            drawLayer(contentLayer)
                        }
                    }
                    // El tinte oscuro va DESPUÉS del contenido desenfocado (no
                    // antes), para que oscurezca lo que se ve a través en vez
                    // de quedar tapado por eso: así el resultado es siempre
                    // oscuro y con buen contraste sin importar qué tan clara
                    // sea la nota que hay detrás.
                    drawRect(barTint.copy(alpha = 0.62f))
                }
        )
        // Capa de primer plano: los botones de verdad, sin desenfocar, con el
        // color forzado a blanco para que resalten sobre el fondo oscuro de
        // la barra sin importar el tema (claro/oscuro) que tenga la app.
        CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = 0.95f)) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
                content = content
            )
        }
    }
}

private enum class ReminderMode { WEEKDAYS, CALENDAR }

// Formatea una fecha del modo calendario: con año para "una vez" (importa
// cuál año exacto), sin año para "cada año" (el año no significa nada ahí,
// se recalcula solo cada vez que pasa).
private fun formatCalendarDate(millis: Long, includeYear: Boolean): String {
    val pattern = if (includeYear) "d MMM yyyy" else "d MMM"
    return java.text.SimpleDateFormat(pattern, java.util.Locale("es")).format(java.util.Date(millis))
}

// Selector de recordatorio para una nota, con dos modos MUTUAMENTE
// EXCLUYENTES (no se puede tener los dos a la vez — guardar en un modo
// vacía al otro):
//  - "Días de la semana": recordatorio recurrente semanal (necesita al
//    menos un día marcado). Usa los componentes nativos de Material3
//    (TimeWheelPicker propio en vez de TimeInput, que usa teclado).
//  - "Calendario": una o más fechas específicas (ej. cumpleaños de varias
//    personas en la misma nota), cada una con su propio "quitar". Con
//    "Una vez", cada fecha suena una sola vez y se descarta sola; con
//    "Cada año", se repiten indefinidamente (el año elegido no importa,
//    solo mes/día).
// Ambos modos comparten el mismo selector de hora (TimeWheelPicker) al
// final.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderPickerSheet(
    initialMillis: Long?,
    initialDays: Set<Int>,
    initialCalendarDates: Set<Long>,
    initialCalendarRecurring: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (millis: Long, days: Set<Int>, calendarDates: Set<Long>, calendarRecurring: Boolean) -> Unit,
    onClear: () -> Unit
) {
    val cal = remember {
        java.util.Calendar.getInstance().apply {
            if (initialMillis != null) {
                timeInMillis = initialMillis
            } else {
                add(java.util.Calendar.HOUR_OF_DAY, 1)
                set(java.util.Calendar.MINUTE, 0)
            }
        }
    }
    var selectedDays by remember { mutableStateOf(initialDays) }
    // Migración suave de notas viejas (de antes de que existiera el modo
    // calendario): si no tienen fechas de calendario guardadas pero sí un
    // reminderAt "simple" (sin días de la semana), se muestran acá como una
    // única fecha en modo calendario "una vez" — mismo comportamiento que
    // tenían antes, ahora expresado con el modelo nuevo.
    var calendarDates by remember {
        mutableStateOf(
            when {
                initialCalendarDates.isNotEmpty() -> initialCalendarDates
                initialDays.isEmpty() && initialMillis != null -> setOf(initialMillis)
                else -> emptySet()
            }
        )
    }
    var calendarRecurring by remember { mutableStateOf(initialCalendarRecurring) }
    var mode by remember {
        mutableStateOf(if (initialDays.isNotEmpty()) ReminderMode.WEEKDAYS else ReminderMode.CALENDAR)
    }

    val datePickerState = rememberDatePickerState(
        // Mismo problema, en sentido inverso: el DatePicker espera recibir
        // "medianoche UTC del día a preseleccionar", no un epoch millis
        // normal en hora local. Si le pasamos cal.timeInMillis tal cual
        // (como hacía la versión anterior), al reabrir un recordatorio ya
        // guardado el calendario podía preseleccionar el día anterior al
        // que realmente se había guardado.
        initialSelectedDateMillis = run {
            val utcMidnight = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            utcMidnight.clear()
            utcMidnight.set(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            utcMidnight.timeInMillis
        }
    )
    // Estado propio de hora/minuto (reemplaza a TimePickerState/TimeInput):
    // se guarda en 24h internamente (0-23), igual que el Calendar de siempre,
    // y se muestra en 12h con AM/PM solo en la UI del disco numérico de más
    // abajo — mismo criterio que se usaba con TimeInput antes. Se comparte
    // entre los dos modos y entre todas las fechas del modo calendario (cada
    // fecha que se agrega usa la hora que esté elegida en ese momento).
    var hour by remember { mutableStateOf(cal.get(java.util.Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(cal.get(java.util.Calendar.MINUTE)) }
    var isPm by remember { mutableStateOf(hour >= 12) }

    // Etiqueta de un carácter + valor de Calendar.DAY_OF_WEEK, mostrados
    // empezando el lunes (más natural en español) aunque Calendar arranca la
    // semana en domingo (SUNDAY=1) — es solo el orden de la UI, el valor
    // guardado es el de Calendar.DAY_OF_WEEK real.
    val weekDays = remember {
        listOf(
            "L" to java.util.Calendar.MONDAY,
            "M" to java.util.Calendar.TUESDAY,
            "X" to java.util.Calendar.WEDNESDAY,
            "J" to java.util.Calendar.THURSDAY,
            "V" to java.util.Calendar.FRIDAY,
            "S" to java.util.Calendar.SATURDAY,
            "D" to java.util.Calendar.SUNDAY
        )
    }

    // Convierte lo elegido en el DatePicker (medianoche UTC del día, ver el
    // comentario de más abajo sobre el bug de zona horaria) + la hora/minuto
    // actual del disco numérico, a un epoch millis real en hora local. Se
    // reusa tanto para "Agregar fecha" en modo calendario.
    fun selectedDateAsMillis(): Long? {
        val selectedDateMillis = datePickerState.selectedDateMillis ?: return null
        val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selectedDateMillis
        }
        val target = java.util.Calendar.getInstance().apply {
            set(
                utcCal.get(java.util.Calendar.YEAR),
                utcCal.get(java.util.Calendar.MONTH),
                utcCal.get(java.util.Calendar.DAY_OF_MONTH),
                hour,
                minute,
                0
            )
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return target.timeInMillis
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                // Cambiar entre modo "días de la semana" (chips, sin
                // DatePicker) y "calendario" (toggle + lista de fechas +
                // DatePicker completo) cambia mucho el alto del contenido de
                // golpe. Sin animateContentSize(), el ModalBottomSheet no se
                // reacomodaba solo a ese nuevo alto — quedaba con el tamaño
                // viejo (recortando contenido nuevo, o dejando un hueco de
                // más) hasta que el usuario lo arrastraba a mano para que
                // Compose volviera a medirlo. Con esto, el cambio de alto se
                // anima solo, sin intervención del usuario.
                .animateContentSize()
                // La hora ya no se elige con teclado (ver TimeWheelPicker,
                // el "disco numérico" de más abajo), pero se deja
                // imePadding() igual como red de seguridad: si el sheet
                // llega a tener algún campo de texto en el futuro, sigue sin
                // quedar tapado por el teclado.
                .imePadding()
        ) {
            Text("Recordatorio", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            // Toggle de modo: los dos son mutuamente excluyentes (no se
            // guarda nunca los dos a la vez, ver el botón "Guardar" más
            // abajo), así que es un selector de a uno, no checkboxes.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp)
            ) {
                listOf(
                    ReminderMode.WEEKDAYS to "Días de la semana",
                    ReminderMode.CALENDAR to "Calendario"
                ).forEach { (m, label) ->
                    val selected = mode == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { mode = m }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (mode) {
                ReminderMode.WEEKDAYS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weekDays.forEach { (label, dayValue) ->
                            val checked = dayValue in selectedDays
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (checked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        selectedDays = if (checked) selectedDays - dayValue else selectedDays + dayValue
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (selectedDays.isEmpty()) {
                            "Marcá al menos un día. Se repite todas las semanas en los días marcados, a la hora de abajo."
                        } else {
                            "Se repite todas las semanas en los días marcados, a la hora de abajo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ReminderMode.CALENDAR -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(3.dp)
                    ) {
                        listOf(false to "Una vez", true to "Cada año").forEach { (rec, label) ->
                            val selected = calendarRecurring == rec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { calendarRecurring = rec }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (calendarRecurring) {
                            "Cada fecha suena todos los años en ese mes/día, indefinidamente."
                        } else {
                            "Cada fecha suena una sola vez y se quita sola de la lista; cuando no queda ninguna, el recordatorio se apaga."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    if (calendarDates.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            calendarDates.sorted().forEach { d ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Text(
                                        formatCalendarDate(d, includeYear = !calendarRecurring),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    IconButton(
                                        onClick = { calendarDates = calendarDates - d },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Quitar fecha",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    DatePicker(state = datePickerState, showModeToggle = false)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            selectedDateAsMillis()?.let { millis ->
                                calendarDates = calendarDates + millis
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar esta fecha a la lista")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            TimeWheelPicker(
                hour = hour,
                minute = minute,
                isPm = isPm,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
                onIsPmChange = { pm ->
                    isPm = pm
                    // Mantiene hour en 24h consistente con el AM/PM elegido,
                    // sin cambiar la hora "de reloj de 12h" que se ve (ej.
                    // "7" con AM pasa a ser 7, con PM pasa a ser 19 — no
                    // salta a otro número, solo cambia de mitad del día).
                    val hour12 = if (hour % 12 == 0) 12 else hour % 12
                    hour = if (pm) (if (hour12 == 12) 12 else hour12 + 12) else (if (hour12 == 12) 0 else hour12)
                }
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (initialMillis != null) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Filled.AlarmOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Quitar")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                val canSave = when (mode) {
                    ReminderMode.WEEKDAYS -> selectedDays.isNotEmpty()
                    ReminderMode.CALENDAR -> calendarDates.isNotEmpty()
                }
                Button(
                    enabled = canSave,
                    onClick = {
                        when (mode) {
                            ReminderMode.WEEKDAYS -> {
                                // Modo días de la semana: no depende de una
                                // fecha elegida en el DatePicker (no se
                                // muestra en este modo), solo de la hora. El
                                // "ancla" que se guarda es HOY a la hora
                                // elegida; ReminderScheduler la usa solo para
                                // sacarle hora/minuto y calcula la próxima
                                // ocurrencia real.
                                val target = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                                    set(java.util.Calendar.MINUTE, minute)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }
                                // Guardar en este modo vacía el de calendario
                                // (mutuamente excluyentes): se pasa
                                // calendarDates = emptySet() a propósito.
                                onConfirm(target.timeInMillis, selectedDays, emptySet(), false)
                            }
                            ReminderMode.CALENDAR -> {
                                // reminderAt acá es solo un resumen (para que
                                // el resto de la app, que chequea
                                // "reminderAt != null" para saber si la nota
                                // tiene un recordatorio activo, siga
                                // funcionando); el que realmente manda es el
                                // conjunto de fechas. Cualquiera de las
                                // fechas sirve como resumen, se usa la más
                                // próxima nada más por prolijidad.
                                val summary = calendarDates.min()
                                // Guardar en este modo vacía el de días de la
                                // semana (mutuamente excluyentes): se pasa
                                // selectedDays = emptySet() a propósito.
                                onConfirm(summary, emptySet(), calendarDates, calendarRecurring)
                            }
                        }
                    }
                ) {
                    Text("Guardar")
                }
            }
            Text(
                "Se envían dos avisos: uno 1 hora antes (notificación normal, " +
                    "sin interrumpir) y otro justo a la hora elegida (flotante). " +
                    "Necesitan el permiso de notificaciones y, en Android 12+, " +
                    "el de \"alarmas exactas\" en Ajustes del sistema para sonar " +
                    "puntual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

// "Disco numérico" para elegir hora y minuto arrastrando el dedo, en vez del
// TimeInput de Material3 (que usa dos campos de texto y abre el teclado del
// sistema al tocarlos). Deslizar hacia ARRIBA sube el número (como si se
// "empujaran" los números de abajo hacia el centro); deslizar hacia ABAJO
// lo baja. También se puede tocar el número chico de arriba/abajo para
// sumar/restar de a uno, para quien prefiera tocar en vez de arrastrar.
@Composable
private fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    isPm: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onIsPmChange: (Boolean) -> Unit
) {
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberWheel(
            value = hour12,
            range = 1..12,
            label = { it.toString() },
            onValueChange = { newHour12 ->
                val newHour24 = if (isPm) (if (newHour12 == 12) 12 else newHour12 + 12) else (if (newHour12 == 12) 0 else newHour12)
                onHourChange(newHour24)
            }
        )
        Text(
            ":",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        NumberWheel(
            value = minute,
            range = 0..59,
            label = { it.toString().padStart(2, '0') },
            onValueChange = onMinuteChange
        )
        Spacer(Modifier.width(16.dp))
        Column {
            listOf(false, true).forEach { pm ->
                val selected = isPm == pm
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable { onIsPmChange(pm) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (pm) "PM" else "AM",
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// Siguiente/anterior valor dentro de range, dando la vuelta al llegar a una
// punta (23 -> 0, 0 -> 23, etc. según el range que se le pase).
private fun wheelStep(value: Int, delta: Int, range: IntRange): Int {
    val span = range.last - range.first + 1
    val idx = ((value - range.first + delta) % span + span) % span
    return range.first + idx
}

@Composable
private fun NumberWheel(
    value: Int,
    range: IntRange,
    label: (Int) -> String,
    onValueChange: (Int) -> Unit
) {
    // Cuántos dp hay que arrastrar para que el número cambie en 1. Se
    // guarda el arrastre "sobrante" (lo que no alcanzó todavía para un paso
    // completo) en dragAccum, para que arrastres lentos y rápidos se
    // sientan proporcionales en vez de saltar de a pasos fijos por gesto.
    val stepDp = 42.dp
    val density = LocalDensity.current
    val stepPx = with(density) { stepDp.toPx() }
    var dragAccum by remember { mutableStateOf(0f) }

    // BUG (encontrado con el disco ya en uso): pointerInput(range) solo
    // reinicia su corrutina cuando CAMBIA la key (acá, `range`, que nunca
    // cambia en la vida de este composable). Compose sí vuelve a crear el
    // lambda de detectVerticalDragGestures en cada recomposición, pero como
    // la corrutina de pointerInput nunca se reinicia, se queda corriendo
    // para siempre con el PRIMER lambda que se le dio — el que tenía
    // "value" y "onValueChange" de la composición inicial, congelados. Con
    // eso, cada paso del arrastre volvía a calcular "siguiente número" a
    // partir de ese valor viejo en vez del actual, así que un arrastre
    // continuo podía terminar salteando números (ej. 2 -> 4 en vez de
    // 2 -> 3 -> 4). rememberUpdatedState mantiene una referencia que sí se
    // actualiza sola en cada recomposición, sin necesidad de reiniciar la
    // corrutina del gesto para leer el valor más reciente.
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)

    val prevValue = wheelStep(value, -1, range)
    val nextValue = wheelStep(value, 1, range)

    // Para que la animación de abajo deslice en la dirección correcta
    // (números subiendo = el nuevo entra por abajo empujando hacia arriba,
    // como en una rueda real) hace falta saber si el último cambio fue "+1"
    // o "-1" — sign(value - anterior) no alcanza solo porque también hay
    // que resolver el caso de dar la vuelta (ej. de 23 a 0 es "+1", no un
    // salto para atrás).
    var lastValueForDirection by remember { mutableStateOf(value) }
    val goingUp = remember(value) {
        val up = lastValueForDirection == value || wheelStep(lastValueForDirection, 1, range) == value
        lastValueForDirection = value
        up
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .pointerInput(range) {
                detectVerticalDragGestures(
                    onDragEnd = { dragAccum = 0f },
                    onDragCancel = { dragAccum = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Arrastrar hacia ARRIBA es dragAmount negativo en
                        // coordenadas de pantalla; queremos que eso SUBA el
                        // número, así que se resta (no se suma) del acumulado.
                        dragAccum -= dragAmount
                        while (dragAccum >= stepPx) {
                            dragAccum -= stepPx
                            currentOnValueChange.value(wheelStep(currentValue.value, 1, range))
                        }
                        while (dragAccum <= -stepPx) {
                            dragAccum += stepPx
                            currentOnValueChange.value(wheelStep(currentValue.value, -1, range))
                        }
                    }
                )
            }
    ) {
        Text(
            label(prevValue),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .clickable { onValueChange(prevValue) }
                .padding(vertical = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            // AnimatedContent con slide vertical: antes el número cambiaba
            // de golpe, sin transición ninguna, así que el disco no se
            // sentía como algo que gira sino como un contador que salta.
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val height = 40
                    if (goingUp) {
                        (slideInVertically(tween(150)) { height } + fadeIn(tween(150)))
                            .togetherWith(slideOutVertically(tween(150)) { -height } + fadeOut(tween(150)))
                    } else {
                        (slideInVertically(tween(150)) { -height } + fadeIn(tween(150)))
                            .togetherWith(slideOutVertically(tween(150)) { height } + fadeOut(tween(150)))
                    }
                },
                label = "wheel-value"
            ) { v ->
                Text(label(v), style = MaterialTheme.typography.displaySmall)
            }
        }
        Text(
            label(nextValue),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .clickable { onValueChange(nextValue) }
                .padding(vertical = 4.dp)
        )
    }
}
