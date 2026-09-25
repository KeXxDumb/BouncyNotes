package com.dumb.bouncynotes.ui.components

// Extraído de NoteEditScreen.kt (que había crecido demasiado) sin cambiar
// nada de la lógica — solo visibilidad `private` -> `internal`.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Barra inferior semitransparente para el editor de notas.
//
// Reemplaza al BottomAppBar de Material3 (que reservaba ~80dp con relleno
// pensado para un FAB embebido que acá no se usa). Esta versión:
//  - tiene una altura fija y compacta (parámetro `height`, 56dp desde donde se
//    llama), en vez de la altura excesiva por defecto.
//  - centra sus botones cuando `centered = true` (usado siempre, en modo
//    edición y en modo vista, para que la barra no "salte" de alineación al
//    cambiar de modo con el ojo/lápiz).
//  - deja ver el contenido de la nota a través suyo con un simple tinte
//    semitransparente (SIN desenfoque — ver el historial más abajo). El
//    contenido de la nota (en NoteEditScreen) se deja dibujar por debajo de
//    esta barra a propósito, extendiéndose hasta el fondo real de la
//    pantalla en vez de detenerse antes.
//
// CAMBIO (auditoría de rendimiento + pedido explícito): esto tenía antes un
// desenfoque real (RenderEffect/BlurEffect sobre una copia grabada del
// contenido en un GraphicsLayer aparte, recortada y vuelta a dibujar acá).
// Android no tiene una API de "blur de lo que hay detrás" (backdrop-filter)
// como en iOS, así que la única forma de fingirlo era grabar TODO el
// contenido de la nota en una capa aparte en cada frame (`contentLayer` en
// NoteEditScreen, con un `.drawWithContent{}` en el Column completo) y
// volver a dibujar una porción de esa grabación acá con el desenfoque
// encima. Esto es contenido costoso de repetir todo el tiempo mientras la
// barra está en pantalla (o sea, siempre que se edita/lee una nota) — más
// notorio en equipos de gama baja. Se reemplaza por una transparencia simple
// (un tinte semitransparente solo, sin grabar ni desenfocar nada): el
// contenido real ya se ve a través porque Compose lo dibuja debajo de forma
// natural (mismo criterio que ya usa la TopAppBar de esta pantalla), sin
// necesidad de fingir nada.
//
// `navBarInset`: alto de la barra de navegación del sistema (0 si el propio
// sistema ya la acomoda solo). Antes, quien llamaba a este componente lo
// envolvía en un `Box(Modifier.navigationBarsPadding())` para no quedar
// tapado por la barra de navegación — pero eso empuja TODA la barra (fondo
// incluido) hacia arriba, dejando una franja vacía y sin nada dibujado justo
// donde estaba la barra de navegación: se veía "cortada y flotando" en vez
// de inmersiva. Ahora el fondo semitransparente de ESTA barra se extiende
// `navBarInset` más abajo (hasta el borde real de la pantalla, por detrás de
// la barra de navegación) y solo los BOTONES quedan arriba de ese inset, así
// el tinte se ve continuo hasta el borde de verdad en vez de dejar un hueco.
@Composable
internal fun GlassBottomBar(
    height: Dp,
    navBarInset: Dp,
    centered: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    // El tinte de la barra es oscuro siempre (sin importar el tema
    // claro/oscuro de la app), y los íconos se fuerzan a un blanco casi puro
    // más abajo, así el contraste es consistente sin importar qué tan clara
    // sea la nota o el tema elegido.
    val barTint = Color.Black
    val barShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Se extiende por detrás de la barra de navegación del sistema
            // (ver comentario grande arriba) — los botones de verdad quedan
            // fijados a los primeros `height` dp, más abajo.
            .height(height + navBarInset)
            // Borde sutil arriba y a los costados para que la barra se
            // distinga claramente del contenido que se ve (transparentado)
            // detrás de ella, en vez de mezclarse con él.
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.18f), shape = barShape)
    ) {
        // Fondo: un solo tinte oscuro semitransparente, sin grabar ni
        // desenfocar nada (ver comentario grande arriba). matchParentSize()
        // hace que cubra TODA la barra, incluida la franja extra de
        // `navBarInset`.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(barShape)
                .background(barTint.copy(alpha = 0.62f))
        )
        // Los botones de verdad, con el color forzado a blanco para que
        // resalten sobre el fondo oscuro sin importar el tema. Altura fija
        // de `height` (NO matchParentSize) y alineados arriba, para que
        // queden en la franja tocable de siempre y no se corran hacia abajo,
        // detrás de la barra de navegación.
        CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = 0.95f)) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(height)
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
