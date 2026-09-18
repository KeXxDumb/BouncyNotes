package com.dumb.bouncynotes.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.VerticalAlignBottom
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.ChecklistItem
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.GalleryLayout
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.buildMediaPickerIntent
import com.dumb.bouncynotes.data.extractPickedUris
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
import com.dumb.bouncynotes.ui.components.NoteBackgroundImage
import com.dumb.bouncynotes.ui.components.RgbColorPicker
import com.dumb.bouncynotes.ui.components.ReminderPickerSheet
import com.dumb.bouncynotes.ui.components.LabelsEditor
import com.dumb.bouncynotes.ui.components.GlassBottomBar
import com.dumb.bouncynotes.ui.components.VideoThumbnailPreview
import com.dumb.bouncynotes.ui.components.rememberImageAspectRatio
import java.io.File

// El modelo del editor segmentado (EditSegment, buildEditSegments,
// segmentsToContent) y el gesto de doble-tap para editar
// (detectDoubleTapToEdit) se extrajeron a NoteEditSegments.kt, mismo
// paquete `ui` — no hace falta ningún import nuevo acá para seguir
// usándolos.
// BUG (reportado): girar la pantalla mientras se crea o edita una nota la
// "resetea" — se pierde todo lo escrito. Causa real: `current` (la nota que
// se está editando, actualizada en CADA cambio: cada letra tipeada, cada
// casilla marcada) vivía en un `remember` simple más abajo. `remember` NO
// sobrevive a que la Activity se destruya y se vuelva a crear, y girar la
// pantalla SIEMPRE hace eso (no hay android:configChanges en el manifest
// que lo evite). El LaunchedEffect(noteId) que carga la nota al entrar solo
// trae de la base de datos la última versión GUARDADA — así que al volver a
// componer desde cero después de girar, una nota NUEVA (noteId == 0)
// arrancaba de un Note() en blanco (se perdía TODO), y una nota YA
// EXISTENTE volvía a como estaba la ÚLTIMA VEZ que se guardó (se perdían los
// cambios de la sesión de edición actual, aunque no fueran nuevos).
//
// La solución es guardar `current` con rememberSaveable (que sí sobrevive,
// porque usa el Bundle de estado de la Activity) en vez de remember. Como
// `Note` no es Parcelable, hace falta un Saver propio que la convierta a un
// String (JSON) y de vuelta — muy parecido a noteToJson/jsonToNote de
// BackupManager, pero ESTE conserva el id real de la nota (BackupManager lo
// fuerza a 0 a propósito, porque está pensado para notas nuevas al importar
// un respaldo; acá necesitamos el id real para seguir guardando sobre la
// misma fila en vez de crear una nota duplicada).
private val NoteStateSaver: Saver<Note, String> = Saver(
    save = { note ->
        val o = JSONObject()
        o.put("id", note.id)
        o.put("type", note.type.name)
        o.put("title", note.title)
        o.put("content", note.content)
        o.put("color", note.color ?: JSONObject.NULL)
        o.put("pinned", note.pinned)
        o.put("archived", note.archived)
        o.put("isPrivate", note.isPrivate)
        o.put("deletedAt", note.deletedAt ?: JSONObject.NULL)
        o.put("createdAt", note.createdAt)
        o.put("updatedAt", note.updatedAt)
        o.put("reminderAt", note.reminderAt ?: JSONObject.NULL)
        val reminderDaysArr = JSONArray()
        note.reminderDays.forEach { reminderDaysArr.put(it) }
        o.put("reminderDays", reminderDaysArr)
        val reminderCalendarDatesArr = JSONArray()
        note.reminderCalendarDates.forEach { reminderCalendarDatesArr.put(it) }
        o.put("reminderCalendarDates", reminderCalendarDatesArr)
        o.put("reminderCalendarRecurring", note.reminderCalendarRecurring)
        val labelsArr = JSONArray()
        note.labels.forEach { labelsArr.put(it) }
        o.put("labels", labelsArr)
        val itemsArr = JSONArray()
        note.checklistItems.forEach { item ->
            val io = JSONObject()
            io.put("text", item.text)
            io.put("checked", item.checked)
            itemsArr.put(io)
        }
        o.put("checklistItems", itemsArr)
        o.toString()
    },
    restore = { json ->
        val o = JSONObject(json)
        val labels = mutableListOf<String>()
        o.optJSONArray("labels")?.let { arr -> for (i in 0 until arr.length()) labels.add(arr.getString(i)) }
        val items = mutableListOf<ChecklistItem>()
        o.optJSONArray("checklistItems")?.let { arr ->
            for (i in 0 until arr.length()) {
                val io = arr.getJSONObject(i)
                items.add(ChecklistItem(text = io.optString("text"), checked = io.optBoolean("checked")))
            }
        }
        Note(
            id = o.optLong("id"),
            type = try { NoteType.valueOf(o.optString("type")) } catch (e: Exception) { NoteType.TEXT },
            title = o.optString("title"),
            content = o.optString("content"),
            checklistItems = items,
            labels = labels,
            color = if (o.isNull("color")) null else o.optString("color"),
            pinned = o.optBoolean("pinned"),
            archived = o.optBoolean("archived"),
            isPrivate = o.optBoolean("isPrivate"),
            deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            reminderAt = if (o.isNull("reminderAt")) null else o.optLong("reminderAt"),
            reminderDays = o.optJSONArray("reminderDays")?.let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }.toSet()
            } ?: emptySet(),
            reminderCalendarDates = o.optJSONArray("reminderCalendarDates")?.let { arr ->
                (0 until arr.length()).map { arr.getLong(it) }.toSet()
            } ?: emptySet(),
            reminderCalendarRecurring = o.optBoolean("reminderCalendarRecurring")
        )
    }
)

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
    var loaded by rememberSaveable { mutableStateOf(false) }
    var current by rememberSaveable(stateSaver = NoteStateSaver) { mutableStateOf(Note(type = initialType)) }
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
    // BUG encontrado: esto arrancaba siempre en `true` y se "corregía" recién
    // en el LaunchedEffect(noteId) de más abajo, según settings.doubleTapToEdit
    // — pero esa corrección pasa DESPUÉS de la primera composición, así que
    // Crossfade/AnimatedContent (más abajo) alcanzan a ver el valor `true`
    // inicial y LUEGO el cambio a `false`, y lo animan como si fuera un
    // cambio de modo real hecho por el usuario. Resultado: abrir una nota
    // que arranca en modo lectura mostraba de encima la animación de "modo
    // lectura", sin que el usuario tocara nada. La solución real es no
    // arrancar con un valor que hay que corregir: noteId y
    // settings.doubleTapToEdit ya están disponibles ACÁ MISMO, sin esperar
    // ningún efecto asincrónico (lo único que sí necesita esperar a la base
    // de datos es el CONTENIDO de la nota, no el modo edición/vista), así
    // que se puede calcular bien de una y no hay nada que re-animar.
    var isEditing by rememberSaveable { mutableStateOf(noteId == 0L || !settings.doubleTapToEdit) }

    // Destello de feedback al cambiar entre modo edición y modo vista: se
    // prende apenas isEditing cambia y se apaga solo, rápido, sin que el
    // usuario tenga que esperarlo (ver el AnimatedVisibility más abajo, y el
    // IconButton del ojo/lápiz que también dispara este cambio).
    //
    // isEditing ya arranca con su valor final (ver el comentario de arriba),
    // así que en teoría esto ya no debería hacer falta para la carga inicial
    // — pero loaded=true como condición extra se deja igual, como red de
    // seguridad barata: si el día de mañana algo más vuelve a reasignar
    // isEditing antes de que la nota termine de cargar, esto sigue evitando
    // que destelle por algo que el usuario no hizo.
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
    // Para el botón de "ir al final" (animateScrollTo/animateScrollToItem
    // son funciones suspend) y para la sincronización de scroll de abajo.
    val scope = rememberCoroutineScope()
    // BUG encontrado: al ser dos LazyListState SEPARADOS, cambiar de modo
    // (ojo/lápiz) mostraba el otro arrancando siempre desde arriba — cada
    // uno tiene su propia posición de scroll independiente, y ninguno se
    // enteraba de dónde había quedado el otro. Esto copia la posición
    // (índice + offset en píxeles dentro de ese índice) del que se estaba
    // viendo HACIA el que pasa a mostrarse, apenas isEditing cambia. No es
    // perfecto — los dos modos no siempre parten el contenido exactamente
    // igual en ítems (un TextField editable no mide igual que el texto de
    // solo lectura) — pero corta de raíz el salto a la parte de arriba, que
    // es lo que se sentía como "se resetea el scroll".
    LaunchedEffect(isEditing) {
        if (isEditing) {
            editLazyListState.scrollToItem(
                viewLazyListState.firstVisibleItemIndex,
                viewLazyListState.firstVisibleItemScrollOffset
            )
        } else {
            viewLazyListState.scrollToItem(
                editLazyListState.firstVisibleItemIndex,
                editLazyListState.firstVisibleItemScrollOffset
            )
        }
    }

    // Para que el recordatorio realmente se vea, en Android 13+ hace falta el
    // permiso de notificaciones. Se pide justo al programar el primer
    // recordatorio, no al abrir la app (evita pedir permisos sin contexto).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* si lo niega, igual queda programada la alarma; solo no se verá la notificación */ }

    LaunchedEffect(noteId) {
        // El "!loaded" es lo que evita que esto pise el estado restaurado
        // por rememberSaveable después de girar la pantalla: `loaded`
        // también sobrevive (ver arriba), así que en una recreación por
        // rotación ya entra en true, y esto NO vuelve a traer la nota de la
        // base de datos (que solo tiene la última versión GUARDADA,
        // desactualizada respecto de lo que había en pantalla). Es una carga
        // real solo la primera vez que esta pantalla se compone de verdad.
        // (isEditing YA NO se toca acá — se calcula bien desde el inicio en
        // su propio rememberSaveable, ver el comentario grande de arriba).
        if (!loaded) {
            if (noteId != 0L) {
                viewModel.getById(noteId)?.let { current = it }
            }
        }
        segments = buildEditSegments(current.content)
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

    // Antes esto no existía: desde el editor solo se podía borrar el GRUPO
    // entero (deleteMediaSegment); para sacar una sola imagen de adentro
    // había que ir al visor a pantalla completa. Opera directo sobre el
    // EditSegment (no sobre el string de contenido vía removeImageOccurrence)
    // porque durante la edición los segments en memoria son la fuente de
    // verdad real, no el content ya serializado.
    fun removeImageFromGallerySeg(segIndex: Int, imageIndexInGroup: Int) {
        val seg = segments.getOrNull(segIndex) as? EditSegment.GallerySeg ?: return
        val fileName = seg.fileNames.getOrNull(imageIndexInGroup) ?: return
        ImageStorage.deleteFile(context, fileName)
        val newFileNames = seg.fileNames.toMutableList().also { it.removeAt(imageIndexInGroup) }
        val newCaptions = seg.captions.toMutableList().also {
            if (imageIndexInGroup < it.size) it.removeAt(imageIndexInGroup)
        }
        val newSegments = segments.toMutableList()
        when {
            // Sin imágenes: el grupo entero desaparece, igual que
            // deleteMediaSegment (con el mismo merge de TextSeg vecinos).
            newFileNames.isEmpty() -> {
                newSegments.removeAt(segIndex)
                if (segIndex > 0 && segIndex < newSegments.size) {
                    val prev = newSegments[segIndex - 1]
                    val next = newSegments[segIndex]
                    if (prev is EditSegment.TextSeg && next is EditSegment.TextSeg) {
                        newSegments[segIndex - 1] = EditSegment.TextSeg(TextFieldValue(prev.value.text + next.value.text))
                        newSegments.removeAt(segIndex)
                    }
                }
                if (newSegments.isEmpty()) newSegments.add(EditSegment.TextSeg(TextFieldValue("")))
            }
            // Un grupo con una sola imagen ya no es "grupo" — mismo criterio
            // que removeImageOccurrence en MarkdownContent.kt.
            newFileNames.size == 1 -> {
                newSegments[segIndex] = EditSegment.ImageSeg(newFileNames[0], newCaptions.getOrElse(0) { "" })
            }
            else -> {
                newSegments[segIndex] = seg.copy(fileNames = newFileNames, captions = newCaptions)
            }
        }
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

    // Extraído a función para poder reusarlo desde los DOS selectores de
    // galería (el picker de fotos nativo y el "clásico" de apps de
    // terceros, ver más abajo) sin duplicar la lógica de copiar/comprimir e
    // insertar.
    fun handlePickedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // BUG DE PERFORMANCE encontrado: ImageStorage.copyFromUri/
        // compressFromUri son funciones comunes (no `suspend`) que hacen
        // I/O de disco real (leer el archivo elegido, decodificar/comprimir
        // si aplica, escribir la copia) — y el callback del selector de
        // medios corre en el hilo PRINCIPAL. Antes, esto trababa la app
        // entera mientras copiaba, bien notorio con fotos grandes de cámara
        // o al elegir varias imágenes de una. withContext(Dispatchers.IO)
        // mueve ese trabajo a un hilo de fondo; el código de después (que
        // sí toca estado de Compose) vuelve solo al hilo principal apenas
        // termina el withContext, sin nada especial que hacer para eso.
        scope.launch {
            val fileNames = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    if (settings.compressImages) {
                        ImageStorage.compressFromUri(context, uri, settings.imageQuality)
                    } else {
                        ImageStorage.copyFromUri(context, uri)
                    }
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
    }

    // ACTION_GET_CONTENT armado a mano (no GetMultipleContents, el contrato
    // que se usaba antes): ese contrato arma el Intent por dentro y no deja
    // apuntarlo a una Activity concreta — necesario para poder saltear el
    // chooser cuando hay una app fijada en Ajustes ("Usar siempre la misma
    // app para elegir imágenes/video").
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> handlePickedImages(extractPickedUris(result.resultCode, result.data)) }

    // Arma el Intent según haya o no una app fijada, y si esa app fijada
    // dejó de existir (desinstalada, etc.) cae al selector genérico en vez
    // de crashear. No se puede limpiar el ajuste desde acá (esta pantalla
    // solo recibe `settings` de lectura, no un mecanismo para actualizarlo
    // como sí tiene SettingsScreen) — el usuario lo puede desactivar a mano
    // en Ajustes si vuelve a fallar.
    fun launchMediaPicker(launcher: ActivityResultLauncher<Intent>, mimeType: String, allowMultiple: Boolean) {
        try {
            launcher.launch(
                buildMediaPickerIntent(
                    mimeType, allowMultiple,
                    pinnedPackage = settings.pinnedMediaPickerPackage,
                    pinnedActivity = settings.pinnedMediaPickerActivity
                )
            )
        } catch (e: ActivityNotFoundException) {
            launcher.launch(buildMediaPickerIntent(mimeType, allowMultiple, "", ""))
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val fileName = pendingCameraFileName
        if (success && fileName != null) {
            if (settings.compressImages) {
                // Mismo motivo que en handlePickedImages: comprimir es I/O de
                // disco real, no algo instantáneo — se saca del hilo principal
                // para no trabar la app justo después de sacar la foto.
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ImageStorage.compressInPlace(context, fileName, settings.imageQuality)
                    }
                    insertImageAtActiveSegment(fileName)
                    focusManager.clearFocus(force = true)
                }
            } else {
                insertImageAtActiveSegment(fileName)
                focusManager.clearFocus(force = true)
            }
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

    // Misma idea que con handlePickedImages: se extrae para reusarla si hace
    // falta más de un selector de video en el futuro.
    fun handlePickedVideo(uri: Uri?) {
        if (uri != null) {
            // Copiar un video (hasta el tope de tamaño permitido, ver
            // ImageStorage) es la operación más pesada de todas las que se
            // movieron a un hilo de fondo en esta vuelta — es la que más se
            // sentía trabar la app antes de este cambio.
            scope.launch {
                val result = withContext(Dispatchers.IO) { ImageStorage.copyVideoFromUri(context, uri) }
                when {
                    result.fileName != null -> {
                        insertVideoAtActiveSegment(result.fileName)
                        focusManager.clearFocus(force = true)
                    }
                    result.tooLarge -> showVideoTooLarge = true
                }
            }
        }
    }

    // Selector clásico para video, mismo motivo que galleryLauncher de arriba.
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> handlePickedVideo(extractPickedUris(result.resultCode, result.data).firstOrNull()) }

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
                            launchMediaPicker(galleryLauncher, "image/*", allowMultiple = true)
                        },
                        Triple(Icons.Filled.Videocam, "Video (máx. ${ImageStorage.MAX_VIDEO_BYTES / (1024 * 1024)} MB)") {
                            showImageSourceDialog = false
                            launchMediaPicker(videoLauncher, "video/*", allowMultiple = false)
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
                android.util.Log.d(
                    "BouncyNotesReminder",
                    "onConfirm recibido: millis=$millis days=$days calendarDates=$calendarDates " +
                        "calendarRecurring=$calendarRecurring (nota id=${current.id})"
                )
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
        // containerColor transparente cuando se muestra el fondo de la nota:
        // si no, el propio Scaffold pinta su fondo opaco (colorScheme.background)
        // DEBAJO del contenido, pero como acá el contenido no llega a cubrir
        // el 100% del área (el Column tiene padding horizontal), sin esto se
        // vería una franja opaca en los bordes en vez de la imagen.
        containerColor = if (settings.showBackgroundInNotes) Color.Transparent else MaterialTheme.colorScheme.background,
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
                },
                // Mismo criterio que la barra superior de la lista de notas
                // (NoteListScreen): semitransparente para dejar ver el fondo
                // en vez de tapar la imagen con una franja sólida.
                colors = if (settings.showBackgroundInNotes) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = settings.topBarOpacity)
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
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
                    // Antes solo se centraba en modo edición (`isEditing`);
                    // en modo lectura quedaba alineado al inicio, así que la
                    // barra "saltaba" de posición al cambiar de modo con el
                    // ojo/lápiz aunque tuviera básicamente los mismos
                    // botones. Ahora siempre centrado, en los dos modos.
                    centered = true
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
                        // BUG encontrado (relacionado con el de arriba, aunque es
                        // un mecanismo distinto): pasar a modo vista sin sacar el
                        // foco a mano dejaba el teclado todavía abierto durante
                        // toda la transición (Crossfade mantiene ambos
                        // contenidos brevemente mientras anima). En Android, el
                        // PRIMER toque al botón atrás del sistema con el
                        // teclado abierto solo cierra el teclado — no llega a
                        // disparar el BackHandler de la nota. Como esto
                        // coincidía justo con la ventana de la animación, se
                        // sentía como "el botón atrás no responde hasta que
                        // termina la animación", cuando en realidad ese primer
                        // toque se lo estaba comiendo el teclado, no la
                        // animación en sí. Sacando el foco acá mismo, de una,
                        // el teclado ya está cerrándose (o cerrado) antes de
                        // que el usuario llegue a tocar atrás.
                        focusManager.clearFocus(force = true)
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
        if (settings.showBackgroundInNotes) {
            // Mismo componente que dibuja el fondo en la lista de notas
            // (NoteBackgroundImage), con la MISMA imagen — pero acá se le
            // resta un poco a la opacidad configurada (una nota tiene mucho
            // más texto para leer que una tarjeta de la lista). Antes esto
            // sumaba una capa negra aparte y terminaba aplastando la imagen
            // mucho más de lo esperado (ver comentario grande en
            // NoteBackgroundImage.kt) — restar unos puntos a la MISMA
            // opacidad es lineal y predecible.
            NoteBackgroundImage(
                settings = settings,
                context = context,
                opacityReduction = 0.15f
            )
        }
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
                            extraBottomInset = bottomBarCompensation,
                            autoSortChecked = settings.autoSortChecked,
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
                                        bringIntoViewRequester = bringIntoViewRequester,
                                        extraBottomInset = bottomBarCompensation
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
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        // Antes esto era una "X" chiquita en la esquina superior
                                        // derecha del grupo entero — muy fácil de confundir con
                                        // las X de borrado individual de cada miniatura (ver
                                        // onDeleteImage abajo), sobre todo cuando la imagen de
                                        // arriba a la derecha del grupo tenía las DOS superpuestas
                                        // casi en el mismo lugar. Ahora es una barra angosta de
                                        // ancho completo, con texto y colores de "peligro" bien
                                        // distintos de cualquier otro botón de la nota, para que
                                        // quede claro que esto borra el GRUPO entero.
                                        TextButton(
                                            onClick = { deleteMediaSegment(index) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) {
                                            Text("Eliminar este grupo de imágenes", style = MaterialTheme.typography.labelMedium)
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        GalleryGrid(
                                            layout = segment.layout,
                                            fileNames = segment.fileNames,
                                            captions = segment.captions,
                                            onImageClick = { i -> viewerStartPos = startIndex + i },
                                            onDeleteImage = { i -> removeImageFromGallerySeg(index, i) },
                                            onCaptionChange = { i, caption ->
                                                val newSegments = segments.toMutableList()
                                                val newCaptions = segment.captions.toMutableList()
                                                while (newCaptions.size <= i) newCaptions.add("")
                                                newCaptions[i] = caption
                                                newSegments[index] = segment.copy(captions = newCaptions)
                                                updateContentFromSegments(newSegments)
                                            }
                                        )
                                    }
                                }
                                is EditSegment.VideoSeg -> {
                                    // Índice plano de este video (misma lógica que
                                    // ImageSeg/GallerySeg de arriba), para abrir el
                                    // visor en la posición correcta al tocarlo.
                                    val imageIndex = segments.take(index).sumOf { s ->
                                        when (s) {
                                            is EditSegment.ImageSeg -> 1
                                            is EditSegment.GallerySeg -> s.fileNames.size
                                            is EditSegment.VideoSeg -> 1
                                            is EditSegment.TextSeg -> 0
                                        }
                                    }
                                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                                        VideoThumbnailPreview(
                                            context = context,
                                            fileName = segment.fileName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(16.dp)),
                                            onClick = { viewerStartPos = imageIndex }
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
                // Botón flotante para saltar directo al final de la nota —
                // pensado para notas largas, donde scrollear todo a mano es
                // tedioso. Solo se muestra si de verdad hay más contenido
                // para abajo (canScrollForward), para no estorbar en notas
                // cortas que ya entran enteras en pantalla. Tiene que ser
                // hijo DIRECTO de este Box (no de una rama del if/else de
                // arriba) para que "align" funcione y para que siempre
                // refleje el scroll actual sin importar el tipo de nota o
                // el modo edición/vista.
                val canScrollToBottom = when {
                    current.type == NoteType.CHECKLIST -> checklistScrollState.canScrollForward
                    isEditing -> editLazyListState.canScrollForward
                    else -> viewLazyListState.canScrollForward
                }
                if (canScrollToBottom) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                when {
                                    current.type == NoteType.CHECKLIST ->
                                        checklistScrollState.animateScrollTo(checklistScrollState.maxValue)
                                    isEditing ->
                                        editLazyListState.animateScrollToItem(
                                            (editLazyListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                        )
                                    else ->
                                        viewLazyListState.animateScrollToItem(
                                            (viewLazyListState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                        )
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = bottomBarCompensation + 12.dp, end = 4.dp)
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(Icons.Filled.VerticalAlignBottom, contentDescription = "Ir al final de la nota")
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

// Este archivo se enfoca en la pantalla de edición/lectura de una nota en sí
// (NoteEditScreen). Las piezas que eran self-contained se extrajeron a
// ui/components/ para no seguir engordando esto — la lógica de todas no
// cambió en nada, solo su ubicación y su visibilidad (`private` ->
// `internal`, ya que siguen sin ser parte de la API pública del módulo):
//  - LabelsEditor.kt
//  - GlassBottomBar.kt
//  - ReminderPickerSheet.kt (+ ReminderMode + formatCalendarDate)
//  - TimeWheelPicker.kt (+ NumberWheel + wheelStep, usado por el anterior)

