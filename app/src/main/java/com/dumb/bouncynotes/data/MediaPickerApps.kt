package com.dumb.bouncynotes.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

// Una app que puede resolver ACTION_GET_CONTENT (galerías de terceros,
// administradores de archivos, Google Photos, etc.) — lo que ya usa la app
// para el selector "clásico" de imágenes/video. Se guarda packageName Y
// activityName (no solo el paquete) porque una misma app puede exponer más
// de una Activity para esto, y para saltear el selector del sistema del
// todo hace falta apuntar a una Activity concreta con Intent.setClassName().
data class PickableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?
)

// Arma el Intent para elegir un archivo, ya sea apuntando directo a la app
// fijada en Ajustes (sin chooser) o al selector "clásico" de siempre
// (chooser con todas las apps compatibles) si no hay ninguna fijada.
fun buildMediaPickerIntent(
    mimeType: String,
    allowMultiple: Boolean,
    pinnedPackage: String,
    pinnedActivity: String
): Intent {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = mimeType
        addCategory(Intent.CATEGORY_OPENABLE)
        if (allowMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }
    if (pinnedPackage.isNotEmpty() && pinnedActivity.isNotEmpty()) {
        intent.setClassName(pinnedPackage, pinnedActivity)
    }
    return intent
}

// Del resultado crudo de StartActivityForResult a la lista de URIs elegidas
// — GetMultipleContents/GetContent (los contratos que se usaban antes) ya
// hacían esto por dentro; al pasar a un Intent armado a mano (necesario para
// poder apuntar a una app fija con setClassName) hay que resolverlo acá.
fun extractPickedUris(resultCode: Int, data: Intent?): List<Uri> {
    if (resultCode != Activity.RESULT_OK || data == null) return emptyList()
    val clipData = data.clipData
    return if (clipData != null) {
        (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }
    } else {
        data.data?.let { listOf(it) } ?: emptyList()
    }
}

// mimeType amplio a propósito ("*/*"): se quiere la lista de apps que
// puedan entregar CUALQUIER archivo por este medio (no solo imágenes),
// porque la app fijada se usa tanto para elegir imágenes como video — filtrar
// por "image/*" dejaría afuera administradores de archivos que declaran un
// filtro más específico o distinto.
fun queryMediaPickerApps(context: Context): List<PickableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    // Antes esto usaba MATCH_DEFAULT_ONLY, que exige que la Activity
    // declare la categoría CATEGORY_DEFAULT en su intent-filter — pensado
    // para resolver UNA sola actividad "por defecto" (como hace
    // resolveActivity()), no para armar una lista de candidatos. Muchos
    // administradores de archivos (MiXplorer, por ejemplo) exponen su
    // Activity de ACTION_GET_CONTENT sin esa categoría, así que quedaban
    // afuera — el chooser real del sistema NO filtra así, muestra
    // cualquier Activity que matchee action+type+category, tenga o no
    // CATEGORY_DEFAULT. Sin flags (0) reproduce ese mismo comportamiento.
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    return resolveInfos
        .distinctBy { it.activityInfo.packageName + "/" + it.activityInfo.name }
        .map { info ->
            val icon = try {
                info.loadIcon(pm).toBitmap().asImageBitmap()
            } catch (e: Exception) {
                null
            }
            PickableApp(
                packageName = info.activityInfo.packageName,
                activityName = info.activityInfo.name,
                label = info.loadLabel(pm).toString(),
                icon = icon
            )
        }
        .sortedBy { it.label.lowercase() }
}
