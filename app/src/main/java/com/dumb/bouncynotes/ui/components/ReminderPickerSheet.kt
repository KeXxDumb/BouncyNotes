package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
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
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------------------
// Selector de recordatorio — rediseño.
//
// Antes: dos toggles anidados ("Días de la semana / Calendario" y, adentro,
// "Una vez / Cada año"), un DatePicker gigante siempre a la vista, un botón
// "Agregar esta fecha a la lista" que había que acordarse de tocar antes de
// "Guardar", y cada fecha agregada se quedaba con la hora que había en ese
// momento (cambiar la hora después NO la afectaba).
//
// Ahora es UNA sola pregunta por sección, en orden:
//   1. ¿Cada cuánto?  Una vez / Cada semana / Cada año  (un solo selector)
//   2. ¿Cuándo?       fechas (con atajos Hoy/Mañana) o días de la semana
//   3. ¿A qué hora?   una sola hora para todo, editable en cualquier momento
//   4. Resumen        "Próximo aviso: mañana a las 9:00 AM" — calculado con
//                     el mismo código que programa la alarma de verdad
//                     (ReminderScheduler.nextTrigger), así lo que se lee acá
//                     es exactamente lo que va a pasar.
// Las fechas se guardan solo como DÍA; la hora se les aplica recién al
// guardar. El contrato con NoteEditScreen (los 4 valores de onConfirm) no
// cambió, así que el modelo de datos ni los respaldos se tocan.
// ---------------------------------------------------------------------------

internal enum class ReminderRepeat(val label: String) {
    ONCE("Una vez"),
    WEEKLY("Cada semana"),
    YEARLY("Cada año")
}

// Formatea una fecha del modo calendario: con año para "una vez" (importa
// cuál año exacto), sin año para "cada año" (el año no significa nada ahí,
// se recalcula solo cada vez que pasa).
internal fun formatCalendarDate(millis: Long, includeYear: Boolean): String {
    val pattern = if (includeYear) "d MMM yyyy" else "d MMM"
    return SimpleDateFormat(pattern, Locale("es")).format(Date(millis))
}

// Lo que el selector le entrega a NoteEditScreen (mismos 4 valores que
// siempre): ver Note.kt para el significado de cada campo.
private class ReminderDraft(
    val millis: Long,
    val days: Set<Int>,
    val dates: Set<Long>,
    val recurring: Boolean
)

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun atTime(dayMillis: Long, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
    timeInMillis = dayMillis
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDayFromToday(offsetDays: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, offsetDays)
}.let { startOfDay(it.timeInMillis) }

// El DatePicker de Material3 trabaja SIEMPRE en UTC: recibe y devuelve
// "medianoche UTC del día". Si se mezcla tal cual con la hora local, el día
// puede correrse uno para atrás (bug ya ocurrido en esta app). Estas dos
// funciones son el único punto donde se convierte entre los dos mundos.
private fun utcMidnightOfLocalDay(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun localDayFromUtcMidnight(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun formatTime(hour: Int, minute: Int): String {
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return "$hour12:${minute.toString().padStart(2, '0')} ${if (hour >= 12) "PM" else "AM"}"
}

private fun formatDateChip(dayMillis: Long, repeat: ReminderRepeat): String {
    if (repeat == ReminderRepeat.YEARLY) return formatCalendarDate(dayMillis, includeYear = false)
    val sameYear = Calendar.getInstance().get(Calendar.YEAR) ==
        Calendar.getInstance().apply { timeInMillis = dayMillis }.get(Calendar.YEAR)
    val pattern = if (sameYear) "EEE d MMM" else "EEE d MMM yyyy"
    return SimpleDateFormat(pattern, Locale("es")).format(Date(dayMillis))
}

// "hoy a las 9:00 AM" / "mañana a las 9:00 AM" / "jueves 25 de septiembre a
// las 9:00 AM" (con año solo si no es el actual).
private fun formatNextTrigger(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val diffDays = Math.round((startOfDay(millis) - startOfDay(System.currentTimeMillis())) / DAY_MILLIS.toDouble()).toInt()
    val dayText = when (diffDays) {
        0 -> "hoy"
        1 -> "mañana"
        else -> {
            val sameYear = cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
            val pattern = if (sameYear) "EEEE d 'de' MMMM" else "EEEE d 'de' MMMM 'de' yyyy"
            SimpleDateFormat(pattern, Locale("es")).format(Date(millis))
        }
    }
    return "$dayText a las ${formatTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}"
}

@Composable
private fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

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
    // --- Estado inicial a partir de lo ya guardado en la nota -------------
    // Notas viejas con un reminderAt "simple" (sin días ni fechas de
    // calendario) se muestran como "Una vez" con esa única fecha.
    val initialTime = remember {
        Calendar.getInstance().apply {
            val base = initialCalendarDates.minOrNull() ?: initialMillis
            if (base != null) {
                timeInMillis = base
            } else {
                // Recordatorio nuevo: por defecto, la próxima hora en punto.
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
            }
        }
    }
    var repeat by remember {
        mutableStateOf(
            when {
                initialDays.isNotEmpty() -> ReminderRepeat.WEEKLY
                initialCalendarDates.isNotEmpty() && initialCalendarRecurring -> ReminderRepeat.YEARLY
                else -> ReminderRepeat.ONCE
            }
        )
    }
    var days by remember { mutableStateOf(initialDays) }
    // Solo el DÍA (medianoche local): la hora se aplica al guardar, así
    // cambiar la hora después afecta a todas las fechas de la lista.
    var dates by remember {
        mutableStateOf(
            when {
                initialCalendarDates.isNotEmpty() -> initialCalendarDates.map { startOfDay(it) }.toSet()
                initialDays.isEmpty() && initialMillis != null -> setOf(startOfDay(initialMillis))
                else -> emptySet()
            }
        )
    }
    var hour by remember { mutableStateOf(initialTime.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(initialTime.get(Calendar.MINUTE)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }

    // --- Lo que se va a guardar, calculado en vivo -------------------------
    val now = System.currentTimeMillis()
    val draft: ReminderDraft? = when (repeat) {
        ReminderRepeat.WEEKLY -> if (days.isEmpty()) null else {
            // El "ancla" es HOY a la hora elegida: ReminderScheduler solo le
            // saca hora/minuto y calcula la próxima ocurrencia real.
            ReminderDraft(atTime(now, hour, minute), days, emptySet(), false)
        }
        ReminderRepeat.ONCE, ReminderRepeat.YEARLY -> if (dates.isEmpty()) null else {
            val full = dates.map { atTime(it, hour, minute) }.toSet()
            // reminderAt es solo un resumen ("la nota tiene recordatorio");
            // el que manda es el conjunto de fechas.
            ReminderDraft(full.min(), emptySet(), full, repeat == ReminderRepeat.YEARLY)
        }
    }
    val nextTrigger: Long? = draft?.let { d ->
        ReminderScheduler.nextTrigger(
            Note(
                reminderAt = d.millis,
                reminderDays = d.days,
                reminderCalendarDates = d.dates,
                reminderCalendarRecurring = d.recurring
            )
        )?.triggerAt
    }
    val canSave = nextTrigger != null

    // Orden de la UI: lunes primero (más natural en español); el valor
    // guardado es el de Calendar.DAY_OF_WEEK real (domingo = 1).
    val weekDays = remember {
        listOf(
            "L" to Calendar.MONDAY, "M" to Calendar.TUESDAY, "X" to Calendar.WEDNESDAY,
            "J" to Calendar.THURSDAY, "V" to Calendar.FRIDAY, "S" to Calendar.SATURDAY,
            "D" to Calendar.SUNDAY
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Recordatorio", style = MaterialTheme.typography.titleLarge)

            // 1. ¿Cada cuánto? --------------------------------------------
            SectionLabel("¿Cada cuánto?")
            SegmentedChoice(
                options = ReminderRepeat.entries.map { it to it.label },
                selected = repeat,
                onSelect = { repeat = it }
            )

            // 2. ¿Cuándo? -------------------------------------------------
            SectionLabel(if (repeat == ReminderRepeat.WEEKLY) "¿Qué días?" else "¿Qué fecha?")
            when (repeat) {
                ReminderRepeat.WEEKLY -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weekDays.forEach { (label, dayValue) ->
                            val checked = dayValue in days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (checked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { days = if (checked) days - dayValue else days + dayValue },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                days = setOf(
                                    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                                    Calendar.THURSDAY, Calendar.FRIDAY
                                )
                            },
                            label = { Text("Lunes a viernes") }
                        )
                        AssistChip(
                            onClick = { days = setOf(Calendar.SATURDAY, Calendar.SUNDAY) },
                            label = { Text("Fin de semana") }
                        )
                        AssistChip(
                            onClick = { days = weekDays.map { it.second }.toSet() },
                            label = { Text("Todos los días") }
                        )
                    }
                }
                ReminderRepeat.ONCE, ReminderRepeat.YEARLY -> {
                    if (repeat == ReminderRepeat.ONCE) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { dates = dates + startOfDayFromToday(0) }, label = { Text("Hoy") })
                            AssistChip(onClick = { dates = dates + startOfDayFromToday(1) }, label = { Text("Mañana") })
                            AssistChip(onClick = { dates = dates + startOfDayFromToday(7) }, label = { Text("En 1 semana") })
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (dates.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dates.sorted().forEach { day ->
                                // Solo en "una vez": una fecha cuyo día+hora ya
                                // pasó nunca va a sonar, se marca en rojo.
                                val isPast = repeat == ReminderRepeat.ONCE && atTime(day, hour, minute) <= now
                                val chipBg = if (isPast) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                                val chipFg = if (isPast) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipBg)
                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Text(formatDateChip(day, repeat), style = MaterialTheme.typography.labelLarge, color = chipFg)
                                    Spacer(Modifier.width(2.dp))
                                    CompactIconButton(
                                        onClick = { dates = dates - day },
                                        icon = Icons.Filled.Close,
                                        contentDescription = "Quitar fecha",
                                        buttonSize = 24.dp,
                                        iconSize = 14.dp,
                                        tint = chipFg
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (dates.isEmpty()) "Elegir fecha" else "Agregar otra fecha")
                    }
                    Text(
                        if (repeat == ReminderRepeat.YEARLY) {
                            "Suena todos los años ese día y mes (por ejemplo, cumpleaños). El año elegido no importa."
                        } else {
                            "Suena en cada fecha y después se quita sola."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // 3. ¿A qué hora? ---------------------------------------------
            SectionLabel("¿A qué hora?")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showTimeDialog = true }) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(formatTime(hour, minute), style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    if (repeat == ReminderRepeat.WEEKLY) "para todos los días marcados" else "para todas las fechas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8, 12, 18, 21).forEach { h ->
                    FilterChip(
                        selected = hour == h && minute == 0,
                        onClick = { hour = h; minute = 0 },
                        label = { Text(formatTime(h, 0)) }
                    )
                }
            }

            // 4. Resumen --------------------------------------------------
            Spacer(Modifier.height(20.dp))
            val summaryBg = if (nextTrigger != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            val summaryFg = if (nextTrigger != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(summaryBg)
                    .padding(14.dp)
            ) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = summaryFg, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    if (nextTrigger != null) {
                        Text("Próximo aviso", style = MaterialTheme.typography.labelMedium, color = summaryFg)
                        Text(formatNextTrigger(nextTrigger), style = MaterialTheme.typography.titleSmall, color = summaryFg)
                        val extra = if (repeat != ReminderRepeat.WEEKLY && dates.size > 1) dates.size - 1 else 0
                        if (extra > 0) {
                            Text(
                                if (extra == 1) "y 1 fecha más" else "y $extra fechas más",
                                style = MaterialTheme.typography.bodySmall,
                                color = summaryFg
                            )
                        }
                        // El aviso previo solo se programa si todavía falta
                        // más de 1 hora (ver ReminderScheduler.schedule).
                        if (nextTrigger - now > 60L * 60L * 1000L) {
                            Text("También avisa 1 hora antes.", style = MaterialTheme.typography.bodySmall, color = summaryFg)
                        }
                    } else {
                        Text(
                            when {
                                repeat == ReminderRepeat.WEEKLY -> "Marca al menos un día para ver cuándo sonará."
                                dates.isEmpty() -> "Elige una fecha para ver cuándo sonará."
                                else -> "Esas fechas ya pasaron a la hora elegida. Elige otra fecha u hora."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = summaryFg
                        )
                    }
                }
            }

            // Botones ------------------------------------------------------
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (initialMillis != null) {
                    TextButton(
                        onClick = onClear,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.AlarmOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Quitar")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(
                    enabled = canSave,
                    onClick = {
                        draft?.let { onConfirm(it.millis, it.days, it.dates, it.recurring) }
                    }
                ) { Text("Guardar") }
            }
            Text(
                "Necesita el permiso de notificaciones y, en Android 12+, el de \"alarmas exactas\" para sonar puntual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

    // --- Diálogo de fecha: solo se compone al abrirlo, así el calendario no
    // ocupa lugar en la hoja cuando no se está usando.
    if (showDatePicker) {
        val todayUtc = remember { utcMidnightOfLocalDay(System.currentTimeMillis()) }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = todayUtc,
            selectableDates = object : SelectableDates {
                // "Una vez" no admite días pasados (nunca sonarían); "cada
                // año" sí, porque solo importan el mes y el día.
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    repeat != ReminderRepeat.ONCE || utcTimeMillis >= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { utc ->
                            dates = dates + localDayFromUtcMidnight(utc)
                        }
                        showDatePicker = false
                    }
                ) { Text("Agregar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }

    // --- Diálogo de hora (el mismo selector de ruedas de siempre) --------
    if (showTimeDialog) {
        var tmpHour by remember { mutableStateOf(hour) }
        var tmpMinute by remember { mutableStateOf(minute) }
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("Hora del recordatorio") },
            text = {
                TimeWheelPicker(
                    hour = tmpHour,
                    minute = tmpMinute,
                    isPm = tmpHour >= 12,
                    onHourChange = { tmpHour = it },
                    onMinuteChange = { tmpMinute = it },
                    // Cambiar AM/PM mantiene la hora "de reloj de 12h" que se
                    // ve (7 AM pasa a 7 PM, no salta a otro número).
                    onIsPmChange = { pm -> tmpHour = (tmpHour % 12) + (if (pm) 12 else 0) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    hour = tmpHour
                    minute = tmpMinute
                    showTimeDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) { Text("Cancelar") }
            }
        )
    }
}
