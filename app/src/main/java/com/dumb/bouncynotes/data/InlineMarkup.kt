package com.dumb.bouncynotes.data

// Edición de formato inline "estilo Word" sobre el texto crudo de una nota.
//
// El formato guardado NO cambia: sigue siendo el texto con marcadores
// (**negrita**, *cursiva*, ~~tachado~~, `mono`), que es lo que leen la vista
// de lectura, la lista, los widgets y la búsqueda. Lo que cambia es el EDITOR:
// los marcadores se ocultan (ver InlineMarkupTransformation) y el usuario nunca
// los escribe ni los ve. Toda edición se calcula en el espacio de los
// caracteres VISIBLES y después se vuelve a serializar a marcadores.
//
// Este archivo es lógica pura (sin Compose), y se probó con un prototipo
// equivalente en Python con pruebas de escenarios y de fuzzing (60.000
// operaciones al azar sin errores; el texto visible resultante siempre
// coincidió con el esperado).
//
// Limitación del formato guardado (no de este código): el parser de lectura
// no anida estilos, así que un tramo tiene UN solo estilo; pedir otro sobre un
// tramo con formato REEMPLAZA al anterior en vez de combinarlos.

enum class InlineStyle(val marker: String) {
    BOLD("**"),
    STRIKE("~~"),
    CODE("`"),
    ITALIC("*")
}

// Un tramo con formato: [open, contentStart) es el marcador de apertura,
// [contentStart, contentEnd) el contenido y [contentEnd, close) el de cierre.
data class InlineSpan(
    val style: InlineStyle,
    val open: Int,
    val contentStart: Int,
    val contentEnd: Int,
    val close: Int
)

// Estilo "armado" para lo próximo que se escriba (como el botón de negrita
// presionado en Word, sin texto seleccionado). style == null significa "sin
// formato" (por ejemplo, se apagó la negrita estando dentro de un tramo en negrita).
data class InlinePending(val style: InlineStyle?)

const val LIST_BULLET = "• "
const val LIST_SUB_BULLET = "    ◦ "

// MISMO orden de prioridad y MISMAS reglas que buildInlineAnnotatedString
// (ui/components/MarkdownText.kt), para que el editor interprete el texto
// exactamente como la vista de lectura.
private val INLINE_ORDER = listOf(InlineStyle.BOLD, InlineStyle.STRIKE, InlineStyle.CODE, InlineStyle.ITALIC)
private val inlineLinkRegex = Regex("""https?://\S+|www\.\S+|[\w.+-]+@[\w-]+\.[\w.-]+""")

private fun findClosingMarker(text: String, from: Int, marker: String): Int? {
    val idx = text.indexOf(marker, from)
    return if (idx == -1 || idx == from) null else idx
}

fun parseInlineSpans(text: String): List<InlineSpan> {
    val spans = ArrayList<InlineSpan>()
    var i = 0
    while (i < text.length) {
        var matched = false
        for (style in INLINE_ORDER) {
            val m = style.marker
            if (text.startsWith(m, i)) {
                val close = findClosingMarker(text, i + m.length, m)
                if (close != null) {
                    spans.add(InlineSpan(style, i, i + m.length, close, close + m.length))
                    i = close + m.length
                    matched = true
                    break
                }
            }
        }
        if (matched) continue
        // Los enlaces se consumen como una unidad (igual que en el renderer),
        // así los marcadores dentro de una URL no se interpretan.
        val link = inlineLinkRegex.matchAt(text, i)
        if (link != null) {
            i = link.range.last + 1
            continue
        }
        i++
    }
    return spans
}

// El texto crudo visto como lo ve el usuario: sin marcadores, con el estilo
// de cada carácter visible y las tablas de correspondencia de posiciones.
class InlineLayout(val raw: String) {
    val spans: List<InlineSpan> = parseInlineSpans(raw)
    val visible: String
    val styles: List<InlineStyle?>
    val visibleToRaw: IntArray
    val visibleSpan: IntArray
    val rawToVisible: IntArray

    init {
        val hidden = BooleanArray(raw.length)
        val styleAtRaw = arrayOfNulls<InlineStyle>(raw.length)
        val spanAtRaw = IntArray(raw.length) { -1 }
        spans.forEachIndexed { idx, s ->
            for (k in s.open until s.contentStart) hidden[k] = true
            for (k in s.contentEnd until s.close) hidden[k] = true
            for (k in s.contentStart until s.contentEnd) {
                styleAtRaw[k] = s.style
                spanAtRaw[k] = idx
            }
        }
        val sb = StringBuilder()
        val st = ArrayList<InlineStyle?>()
        val v2r = ArrayList<Int>()
        val vsp = ArrayList<Int>()
        val r2v = IntArray(raw.length + 1)
        for (k in raw.indices) {
            r2v[k] = sb.length
            if (!hidden[k]) {
                sb.append(raw[k])
                st.add(styleAtRaw[k])
                v2r.add(k)
                vsp.add(spanAtRaw[k])
            }
        }
        r2v[raw.length] = sb.length
        visible = sb.toString()
        styles = st
        visibleToRaw = v2r.toIntArray()
        visibleSpan = vsp.toIntArray()
        rawToVisible = r2v
    }

    // Posición cruda del cursor "justo después" del carácter visible número
    // v-1 (y antes de cualquier marcador que venga a continuación). Es la
    // posición canónica del cursor: al final de un tramo con formato queda
    // ADENTRO (antes del marcador de cierre) y al principio queda AFUERA
    // (antes del marcador de apertura), igual que en Word, donde el texto
    // nuevo hereda el formato del carácter anterior.
    fun rawAfterVisible(v: Int): Int {
        if (v <= 0) return 0
        val k = minOf(v, visible.length)
        if (k == 0) return 0
        return visibleToRaw[k - 1] + 1
    }
}

// ---------------------------------------------------------------------------
// Reescritura de una ventana de texto en el espacio visible
// ---------------------------------------------------------------------------

private fun serializeRuns(chars: String, styles: List<InlineStyle?>): String {
    val out = StringBuilder()
    var i = 0
    while (i < chars.length) {
        val st = styles[i]
        var j = i
        while (j < chars.length && styles[j] == st) j++
        if (st == null) {
            out.append(chars, i, j)
        } else {
            out.append(st.marker).append(chars, i, j).append(st.marker)
        }
        i = j
    }
    return out.toString()
}

// Reemplaza los caracteres VISIBLES [vs, ve) por `ins` (con el estilo
// `insStyle`) y devuelve el nuevo texto crudo. Solo se reescribe la ventana de
// tramos que toca la edición (más los vecinos con el mismo estilo, para
// fusionarlos); el resto del texto se copia tal cual. Devuelve null si el
// resultado NO se interpretaría exactamente como se esperaba (por ejemplo, si el
// usuario escribió marcadores literales que chocan con los del formato): en ese
// caso el llamador usa el texto que produjo el teclado, sin tocarlo.
internal fun applyVisibleEdit(layout: InlineLayout, vs: Int, ve: Int, ins: String, insStyle: InlineStyle?): String? {
    val n = layout.visible.length
    val expectedVisible = layout.visible.substring(0, vs) + ins + layout.visible.substring(ve)
    val expectedStyles = ArrayList<InlineStyle?>(expectedVisible.length)
    for (k in 0 until vs) expectedStyles.add(layout.styles[k])
    repeat(ins.length) { expectedStyles.add(insStyle) }
    for (k in ve until n) expectedStyles.add(layout.styles[k])

    val newRaw: String
    if (n == 0) {
        newRaw = serializeRuns(ins, List(ins.length) { insStyle })
    } else {
        fun runStart(k0: Int): Int {
            var k = k0
            while (k > 0 && layout.styles[k - 1] == layout.styles[k]) k--
            return k
        }
        fun runEnd(k0: Int): Int {
            var k = k0
            while (k + 1 < n && layout.styles[k + 1] == layout.styles[k]) k++
            return k + 1
        }
        fun rawStartOf(k: Int): Int =
            if (layout.styles[k] != null) layout.spans[layout.visibleSpan[k]].open else layout.visibleToRaw[k]
        fun rawEndOf(k: Int): Int =
            if (layout.styles[k] != null) layout.spans[layout.visibleSpan[k]].close else layout.visibleToRaw[k] + 1

        val a = if (vs > 0) runStart(vs - 1) else vs
        val b = if (ve < n) runEnd(ve) else n
        val rs = rawStartOf(a)
        val re = rawEndOf(b - 1)

        val winChars = StringBuilder()
        val winStyles = ArrayList<InlineStyle?>()
        for (k in a until vs) {
            winChars.append(layout.visible[k])
            winStyles.add(layout.styles[k])
        }
        for (c in ins) {
            winChars.append(c)
            winStyles.add(insStyle)
        }
        for (k in ve until b) {
            winChars.append(layout.visible[k])
            winStyles.add(layout.styles[k])
        }
        newRaw = layout.raw.substring(0, rs) + serializeRuns(winChars.toString(), winStyles) + layout.raw.substring(re)
    }
    val check = InlineLayout(newRaw)
    return if (check.visible == expectedVisible && check.styles == expectedStyles) newRaw else null
}

// ---------------------------------------------------------------------------
// Partir el texto crudo en una posición (para insertar imágenes, etc.)
// ---------------------------------------------------------------------------

// Parte `raw` en `p` dejando las dos mitades "balanceadas": si el cursor está
// DENTRO de un tramo con formato, la primera mitad lo cierra y la segunda lo
// vuelve a abrir (o no, si el cursor está al borde). Sin esto, cada mitad
// quedaría con un marcador huérfano que se vería como asteriscos sueltos.
fun splitRawAt(raw: String, p: Int): Pair<String, String> {
    val pos = p.coerceIn(0, raw.length)
    val span = parseInlineSpans(raw).firstOrNull { pos > it.open && pos < it.close }
        ?: return raw.substring(0, pos) to raw.substring(pos)
    val m = span.style.marker
    return when {
        pos <= span.contentStart -> raw.substring(0, span.open) to raw.substring(span.open)
        pos >= span.contentEnd -> raw.substring(0, span.close) to raw.substring(span.close)
        else -> (raw.substring(0, pos) + m) to (m + raw.substring(pos))
    }
}

// ---------------------------------------------------------------------------
// Ediciones del teclado
// ---------------------------------------------------------------------------

data class InlineEditOutcome(
    val raw: String,
    val selStart: Int,
    val selEnd: Int,
    val pending: InlinePending?
)

private data class RawEdit(val start: Int, val end: Int, val inserted: String)

// Deduce QUÉ cambió el teclado (rango crudo eliminado + texto insertado). Usa
// la selección vieja y nueva para desambiguar cuando hay caracteres repetidos
// (escribir una "a" al lado de otra "a"), porque la posición exacta importa
// para decidir de qué formato es el carácter nuevo.
private fun resolveEdit(
    old: String, oldSelStart: Int, oldSelEnd: Int,
    new: String, newSelStart: Int, newSelEnd: Int
): RawEdit {
    val a = minOf(oldSelStart, oldSelEnd).coerceIn(0, old.length)
    val b = maxOf(oldSelStart, oldSelEnd).coerceIn(0, old.length)
    val ol = old.length
    val nl = new.length
    if (nl > ol) {
        val k = nl - ol
        val p = newSelStart - k
        if (newSelStart == newSelEnd && p >= 0 && p + k <= nl && new.substring(0, p) + new.substring(p + k) == old) {
            return RawEdit(p, p, new.substring(p, p + k))
        }
    }
    if (nl < ol) {
        val k = ol - nl
        if (a != b && b - a == k && old.substring(0, a) + old.substring(b) == new) return RawEdit(a, b, "")
        if (a == b) {
            if (a - k >= 0 && old.substring(0, a - k) + old.substring(a) == new) return RawEdit(a - k, a, "")
            if (a + k <= ol && old.substring(0, a) + old.substring(a + k) == new) return RawEdit(a, a + k, "")
        }
    }
    if (a != b) {
        val xEnd = nl - (ol - b)
        if (xEnd >= a && old.substring(0, a) + new.substring(a, xEnd) + old.substring(b) == new) {
            return RawEdit(a, b, new.substring(a, xEnd))
        }
    }
    var cp = 0
    while (cp < minOf(ol, nl) && old[cp] == new[cp]) cp++
    var cs = 0
    while (cs < minOf(ol, nl) - cp && old[ol - 1 - cs] == new[nl - 1 - cs]) cs++
    return RawEdit(cp, ol - cs, new.substring(cp, nl - cs))
}

private fun visibleLineStart(visible: String, v: Int): Int {
    if (v <= 0) return 0
    val i = visible.lastIndexOf('\n', v - 1)
    return if (i == -1) 0 else i + 1
}

private fun bulletPrefixAt(visible: String, lineStart: Int): String? = when {
    visible.startsWith(LIST_SUB_BULLET, lineStart) -> LIST_SUB_BULLET
    visible.startsWith(LIST_BULLET, lineStart) -> LIST_BULLET
    else -> null
}

// Procesa un cambio que hizo el teclado (escribir, borrar, pegar, reemplazar
// la selección) para que el formato se comporte como en Word:
//  - el texto nuevo hereda el formato del carácter anterior, o el estilo
//    "armado" (`pending`) si se tocó un botón de formato sin selección;
//  - borrar nunca deja marcadores huérfanos, ni se pueden borrar a medias;
//  - los puntos de lista son un bloque: Enter continúa la lista, Enter en un
//    punto vacío lo quita, y borrar justo después del punto lo quita (un
//    subpunto primero pasa a punto).
// Si no se puede garantizar que el resultado se interprete bien, devuelve el
// texto del teclado tal cual.
fun processInlineEdit(
    oldRaw: String, oldSelStart: Int, oldSelEnd: Int,
    newRaw: String, newSelStart: Int, newSelEnd: Int,
    pending: InlinePending?
): InlineEditOutcome {
    if (oldRaw == newRaw) {
        // Solo cambió la selección: el estilo armado deja de valer (como en
        // Word, al mover el cursor el formato vuelve al del texto de ahí).
        val moved = oldSelStart != newSelStart || oldSelEnd != newSelEnd
        return InlineEditOutcome(newRaw, newSelStart, newSelEnd, if (moved) null else pending)
    }
    val layout = InlineLayout(oldRaw)
    val edit = resolveEdit(oldRaw, oldSelStart, oldSelEnd, newRaw, newSelStart, newSelEnd)
    val rs = edit.start.coerceIn(0, oldRaw.length)
    val re = edit.end.coerceIn(rs, oldRaw.length)
    var vs = layout.rawToVisible[rs]
    var ve = layout.rawToVisible[re]
    var ins = edit.inserted
    val v = layout.visible
    val n = v.length

    if (ins.isEmpty() && vs == ve) {
        // Solo se tocaron caracteres ocultos (marcadores): el usuario quiso
        // borrar un carácter VISIBLE; hacia atrás si lo borrado quedaba antes
        // del cursor, hacia adelante si no.
        val oldCaret = minOf(oldSelStart, oldSelEnd)
        if (re <= oldCaret && vs > 0) {
            vs -= 1
        } else if (vs < n) {
            ve = vs + 1
        } else {
            return InlineEditOutcome(oldRaw, oldSelStart, oldSelEnd, pending)
        }
    }

    if (ins == "\n" && vs == ve) {
        val ls = visibleLineStart(v, vs)
        val prefix = bulletPrefixAt(v, ls)
        if (prefix != null && vs >= ls + prefix.length) {
            val lineEndIdx = v.indexOf('\n', vs)
            val lineEnd = if (lineEndIdx == -1) n else lineEndIdx
            if (lineEnd == ls + prefix.length && vs == lineEnd) {
                // Enter en un punto vacío: se quita el punto (termina la lista), sin salto de línea.
                vs = ls
                ve = ls + prefix.length
                ins = ""
            } else {
                // Enter en un punto con texto: la lista continúa con otro punto.
                ins = "\n" + prefix
            }
        }
    } else if (ins.isEmpty() && ve - vs == 1) {
        val ls = visibleLineStart(v, vs)
        val prefix = bulletPrefixAt(v, ls)
        if (prefix != null && ve == ls + prefix.length) {
            // Borrar justo después del punto: se quita todo el bloque (un subpunto pasa a punto).
            vs = ls
            ve = ls + prefix.length
            ins = if (prefix == LIST_SUB_BULLET) LIST_BULLET else ""
        }
    }

    val insStyle: InlineStyle? = when {
        ins.isEmpty() -> null
        pending != null -> pending.style
        ve > vs -> layout.styles[vs]
        vs > 0 -> layout.styles[vs - 1]
        else -> null
    }
    val result = applyVisibleEdit(layout, vs, ve, ins, insStyle)
        ?: return InlineEditOutcome(newRaw, newSelStart, newSelEnd, null)
    val caret = InlineLayout(result).rawAfterVisible(vs + ins.length)
    return InlineEditOutcome(result, caret, caret, null)
}

// ---------------------------------------------------------------------------
// Botones de formato
// ---------------------------------------------------------------------------

// Estilo que "está activo" para el botón correspondiente: el armado
// (pending) si lo hay; si no, el del carácter anterior al cursor; con texto
// seleccionado, el estilo común de toda la selección (o null si es mixta).
fun activeInlineStyle(raw: String, selStart: Int, selEnd: Int, pending: InlinePending?): InlineStyle? {
    val layout = InlineLayout(raw)
    val a = minOf(selStart, selEnd).coerceIn(0, raw.length)
    val b = maxOf(selStart, selEnd).coerceIn(0, raw.length)
    val vs = layout.rawToVisible[a]
    val ve = layout.rawToVisible[b]
    if (vs == ve) {
        if (pending != null) return pending.style
        return if (vs > 0) layout.styles[vs - 1] else null
    }
    val first = layout.styles[vs]
    for (k in vs until ve) if (layout.styles[k] != first) return null
    return first
}

// Toca un botón de formato. Sin selección: arma (o desarma) el estilo para lo
// que se escriba a continuación, sin tocar el texto. Con selección: aplica el
// estilo a toda la selección, o se lo quita si ya lo tenía toda.
fun toggleInlineStyle(
    raw: String, selStart: Int, selEnd: Int,
    style: InlineStyle, pending: InlinePending?
): InlineEditOutcome {
    val layout = InlineLayout(raw)
    val a = minOf(selStart, selEnd).coerceIn(0, raw.length)
    val b = maxOf(selStart, selEnd).coerceIn(0, raw.length)
    val vs = layout.rawToVisible[a]
    val ve = layout.rawToVisible[b]
    if (vs == ve) {
        val effective = if (pending != null) pending.style else (if (vs > 0) layout.styles[vs - 1] else null)
        return InlineEditOutcome(raw, selStart, selEnd, InlinePending(if (effective == style) null else style))
    }
    var all = true
    for (k in vs until ve) if (layout.styles[k] != style) { all = false; break }
    val target: InlineStyle? = if (all) null else style
    val newRaw = applyVisibleEdit(layout, vs, ve, layout.visible.substring(vs, ve), target)
        ?: return InlineEditOutcome(raw, selStart, selEnd, null)
    val nl = InlineLayout(newRaw)
    return InlineEditOutcome(newRaw, nl.rawAfterVisible(vs), nl.rawAfterVisible(ve), null)
}

// Botones de punto / subpunto: quitan, cambian o ponen el prefijo de lista en
// la línea del cursor, trabajando en el espacio visible (así funciona aunque la
// línea tenga formato). Devuelve (nuevo texto crudo, nueva posición del cursor)
// o null si no se pudo aplicar.
fun toggleListPrefix(raw: String, caretRaw: Int, prefix: String): Pair<String, Int>? {
    val layout = InlineLayout(raw)
    val v = layout.visible
    val cv = layout.rawToVisible[caretRaw.coerceIn(0, raw.length)]
    val ls = visibleLineStart(v, cv)
    val current = bulletPrefixAt(v, ls)
    val vs = ls
    val ve: Int
    val ins: String
    when {
        current == prefix -> { ve = ls + prefix.length; ins = "" }
        current != null -> { ve = ls + current.length; ins = prefix }
        else -> { ve = ls; ins = prefix }
    }
    val newRaw = applyVisibleEdit(layout, vs, ve, ins, null) ?: return null
    val newCaretVisible = maxOf(ls, cv + ins.length - (ve - vs))
    return newRaw to InlineLayout(newRaw).rawAfterVisible(newCaretVisible)
}
