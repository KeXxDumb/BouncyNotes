package com.dumb.bouncynotes.ui.components

// Extraído de NoteEditScreen.kt (que había crecido demasiado) sin cambiar
// nada de la lógica — solo visibilidad `private` -> `internal`.

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Barra inferior "de vidrio esmerilado" para el editor de notas.
//
// Reemplaza al BottomAppBar de Material3 (que reservaba ~80dp con relleno
// pensado para un FAB embebido que acá no se usa). Esta versión:
//  - tiene una altura fija y compacta (parámetro `height`, 56dp desde donde se
//    llama), en vez de la altura excesiva por defecto.
//  - centra sus botones cuando `centered = true` (usado siempre, en modo
//    edición y en modo vista, para que la barra no "salte" de alineación al
//    cambiar de modo con el ojo/lápiz).
//  - es semitransparente y desenfoca lo que hay detrás en vez de tapar todo
//    con un panel sólido. El contenido de la nota (en NoteEditScreen) se
//    deja dibujar por debajo de esta barra a propósito, grabando su dibujo
//    en `contentLayer`; acá simplemente volvemos a dibujar (recortada a esta
//    franja) esa misma grabación con un desenfoque real encima, así que lo
//    que se ve "a través" de la barra es efectivamente el contenido real que
//    hay detrás, no una imitación.
@Composable
internal fun GlassBottomBar(
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
