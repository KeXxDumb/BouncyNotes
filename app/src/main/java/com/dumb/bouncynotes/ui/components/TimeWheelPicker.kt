package com.dumb.bouncynotes.ui.components

// Extraído de NoteEditScreen.kt (que había crecido demasiado) sin cambiar
// nada de la lógica — solo visibilidad `private` -> `internal` (sigue sin
// ser parte de la API pública del módulo, pero ahora es visible desde
// ReminderPickerSheet.kt, que quedó en un archivo aparte).

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun TimeWheelPicker(
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
internal fun wheelStep(value: Int, delta: Int, range: IntRange): Int {
    val span = range.last - range.first + 1
    val idx = ((value - range.first + delta) % span + span) % span
    return range.first + idx
}

@Composable
internal fun NumberWheel(
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
