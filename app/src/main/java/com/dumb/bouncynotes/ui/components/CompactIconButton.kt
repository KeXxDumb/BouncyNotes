package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Botón de ícono de tamaño EXACTO, para reemplazar a IconButton cuando se
// necesita uno chico (la X de quitar, el borrar, etc).
//
// Por qué no alcanza con IconButton(modifier = Modifier.size(22.dp)): el
// IconButton de Material3 le aplica por dentro minimumInteractiveComponentSize()
// (área táctil mínima de 48dp) DESPUÉS del size() de afuera. El size() de
// afuera fuerza el botón a medir 22dp, pero ese cálculo interno sigue
// centrando el contenido como si el botón midiera 48dp, así que el ícono
// queda corrido hacia abajo/derecha y "se sale" del lugar donde está (fuera
// del fondo redondo, del ítem, de la fila...). Acá no hay ese cálculo: el
// tamaño es el que se pide y el ícono queda siempre centrado.
//
// El clip va ANTES del clickable para que el ripple quede circular (mismo
// criterio que el resto de la app).
@Composable
fun CompactIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    tint: Color = LocalContentColor.current,
    containerColor: Color = Color.Transparent
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
