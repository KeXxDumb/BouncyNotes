package com.dumb.bouncynotes.ui.components

// Extraído de NoteEditScreen.kt (que había crecido demasiado) sin cambiar
// nada de la lógica — solo visibilidad `private` -> `internal`, para que
// NoteEditScreen.kt lo siga pudiendo usar desde el otro archivo.

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal enum class ReminderMode { WEEKDAYS, CALENDAR }

// Formatea una fecha del modo calendario: con año para "una vez" (importa
// cuál año exacto), sin año para "cada año" (el año no significa nada ahí,
// se recalcula solo cada vez que pasa).
internal fun formatCalendarDate(millis: Long, includeYear: Boolean): String {
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
internal fun ReminderPickerSheet(
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
        // BUG CRÍTICO (reportado): guardar un recordatorio de calendario no
        // guardaba nada. Causa real, confirmada con logcat: el botón
        // "Guardar" de ESTE sheet nunca se llegaba a tocar — combinar
        // .animateContentSize() directo con .verticalScroll() en el MISMO
        // Modifier es una combinación con un problema conocido en Compose:
        // verticalScroll mide su contenido con alto "infinito" (sin límite)
        // para saber cuánto hay para scrollear, pero animateContentSize
        // anima ese alto en dos pasadas — cuando el contenido crece (por
        // ejemplo, al tocar "Agregar fecha" y sumar un chip a la lista), el
        // scroll puede quedar con los límites calculados ANTES de que
        // termine de crecer, dejando la parte de abajo (el botón "Guardar")
        // inalcanzable por scroll aunque esté ahí, en el árbol de layout.
        // El log lo mostraba clarito: "Agregar fecha tocado" se registraba
        // bien, pero nunca llegaba el "onConfirm recibido" — la nota se
        // terminaba guardando por el botón normal de la nota (que no toca
        // el recordatorio para nada), sin que el diálogo llegara a
        // confirmarse nunca.
        //
        // El arreglo: separar las dos responsabilidades en dos Column
        // anidadas — la de AFUERA solo scrollea (sin animar tamaño), la de
        // ADENTRO (el contenido real) es la que anima su propio tamaño. Así
        // verticalScroll mide sobre un contenido que ya terminó de animarse,
        // en vez de mezclarse con la animación.
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                // La hora ya no se elige con teclado (ver TimeWheelPicker,
                // el "disco numérico" de más abajo), pero se deja
                // imePadding() igual como red de seguridad: si el sheet
                // llega a tener algún campo de texto en el futuro, sigue sin
                // quedar tapado por el teclado.
                .imePadding()
        ) {
        Column(modifier = Modifier.animateContentSize()) {
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

                    // Material3 no tiene una variante de DatePicker que
                    // oculte el año (solo existe el selector completo) —
                    // no se puede sacar de la UI sin construir un selector
                    // propio desde cero. Como paso intermedio, se aclara acá
                    // que el año elegido abajo no importa en este modo (el
                    // código YA lo ignora al calcular cuándo suena, ver
                    // ReminderScheduler.nextCalendarTrigger).
                    if (calendarRecurring) {
                        Text(
                            "El año que elijas abajo no importa en este modo — solo se usan el mes y el día.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }

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
                                    CompactIconButton(
                                        onClick = { calendarDates = calendarDates - d },
                                        icon = Icons.Filled.Close,
                                        contentDescription = "Quitar fecha",
                                        buttonSize = 22.dp,
                                        iconSize = 14.dp,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    DatePicker(state = datePickerState, showModeToggle = false)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val millis = selectedDateAsMillis()
                            android.util.Log.d(
                                "BouncyNotesReminder",
                                "Agregar fecha tocado: selectedDateAsMillis()=$millis " +
                                    "(datePickerState.selectedDateMillis=${datePickerState.selectedDateMillis}, hour=$hour, minute=$minute)"
                            )
                            millis?.let { calendarDates = calendarDates + it }
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
                                android.util.Log.d(
                                    "BouncyNotesReminder",
                                    "Guardar (modo calendario) tocado: calendarDates=$calendarDates " +
                                        "calendarRecurring=$calendarRecurring summary=$summary"
                                )
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
        } // cierra el Column interno (animateContentSize)
        } // cierra el Column externo (verticalScroll)
    }
}
