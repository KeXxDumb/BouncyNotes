package com.dumb.bouncynotes.data

import android.content.Context

/**
 * Espejo SINCRÓNICO (SharedPreferences) de AppSettings, usado únicamente para
 * pintar el primer frame de la UI sin parpadeo.
 *
 * ¿Por qué no alcanza con esperar el Flow real de DataStore (que es la fuente
 * de verdad, y sigue siéndolo)? Porque ese Flow siempre entrega su primer
 * valor a través de una corrutina, y Kotlin/Compose despachan esa corrutina
 * DESPUÉS de que el frame actual ya se compuso — así que la primerísima
 * composición de la Activity SIEMPRE ve "todavía no hay valor", sin importar
 * cuán rápido sea el disco o qué tan simple sea la lectura. Ese primer frame
 * "sin valor" es justamente el que se veía negro (con tema oscuro/valores de
 * fábrica) antes de saltar al valor real un instante después.
 *
 * SharedPreferences, en cambio, se puede leer de forma 100% sincrónica ANTES
 * de setContent(), así que su valor (el de la última vez que se guardó algo,
 * no necesariamente "fresco") ya está disponible en el primerísimo frame. Se
 * actualiza cada vez que el Flow real de DataStore entrega un valor nuevo
 * (ver el .onEach en SettingsRepository.settings), así que después del
 * primerísimo arranque de la app (donde no hay nada cacheado todavía y se
 * usan los AppSettings() de fábrica) siempre refleja los últimos valores
 * reales guardados — y por lo tanto ya no hay ningún salto visible.
 */
object SettingsCache {
    private const val PREFS_NAME = "settings_cache"

    private object K {
        const val THEME_MODE = "theme_mode"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val SEED_COLOR = "seed_color"
        const val GRID_COLUMNS = "grid_columns"
        const val NOTE_LAYOUT = "note_layout"
        const val FONT_SCALE = "font_scale"
        const val SORT_ORDER = "sort_order"
        const val CHECKBOX_POSITION = "checkbox_position"
        const val AUTO_SORT_CHECKED = "auto_sort_checked"
        const val CONFIRM_DELETE = "confirm_delete"
        const val START_VIEW = "start_view"
        const val LAST_VIEW_MODE = "last_view_mode"
        const val BIOMETRIC_REMEMBER_MIN = "biometric_remember_min"
        const val HIDE_FROM_RECENTS = "hide_from_recents"
        const val APP_WIDE_LOCK = "app_wide_lock"
        const val USE_TRASH = "use_trash"
        const val TRASH_PURGE_DAYS = "trash_purge_days"
        const val COMPRESS_IMAGES = "compress_images"
        const val IMAGE_QUALITY = "image_quality"
        const val DOUBLE_TAP_EDIT = "double_tap_edit"
        const val BACKGROUND_IMAGE = "background_image"
        const val BACKGROUND_MONOCHROME = "background_monochrome"
        const val BACKGROUND_FADE = "background_fade"
        const val BACKGROUND_FADE_OPACITY = "background_fade_opacity_pct"
        const val TOP_BAR_OPACITY = "top_bar_opacity_pct"
        const val BACKGROUND_IMAGE_OPACITY = "background_image_opacity_pct"
        const val SHOW_FIRST_IMAGE = "show_first_image"
        const val DEFAULT_GALLERY_LAYOUT = "default_gallery_layout"
        const val TITLE_MODE = "title_mode"
        const val CUSTOM_TITLE_TEXT = "custom_title_text"
        const val RIGHT_EDGE_SWIPE_ACTION = "right_edge_swipe_action"
    }

    /** Lectura 100% sincrónica. Segura de llamar antes de setContent(). */
    fun read(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val d = AppSettings() // valores de fábrica, por si es el primerísimo arranque
        return AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString(K.THEME_MODE, null) ?: d.themeMode.name) }.getOrDefault(d.themeMode),
            dynamicColor = prefs.getBoolean(K.DYNAMIC_COLOR, d.dynamicColor),
            seedColorHex = prefs.getString(K.SEED_COLOR, d.seedColorHex) ?: d.seedColorHex,
            gridColumns = prefs.getInt(K.GRID_COLUMNS, d.gridColumns),
            noteLayout = runCatching { NoteLayout.valueOf(prefs.getString(K.NOTE_LAYOUT, null) ?: d.noteLayout.name) }.getOrDefault(d.noteLayout),
            fontScale = runCatching { FontScale.valueOf(prefs.getString(K.FONT_SCALE, null) ?: d.fontScale.name) }.getOrDefault(d.fontScale),
            sortOrder = runCatching { SortOrder.valueOf(prefs.getString(K.SORT_ORDER, null) ?: d.sortOrder.name) }.getOrDefault(d.sortOrder),
            checkboxPosition = runCatching { CheckboxPosition.valueOf(prefs.getString(K.CHECKBOX_POSITION, null) ?: d.checkboxPosition.name) }.getOrDefault(d.checkboxPosition),
            autoSortChecked = prefs.getBoolean(K.AUTO_SORT_CHECKED, d.autoSortChecked),
            confirmBeforeDelete = prefs.getBoolean(K.CONFIRM_DELETE, d.confirmBeforeDelete),
            startView = runCatching { StartView.valueOf(prefs.getString(K.START_VIEW, null) ?: d.startView.name) }.getOrDefault(d.startView),
            lastViewMode = prefs.getString(K.LAST_VIEW_MODE, d.lastViewMode) ?: d.lastViewMode,
            biometricRememberMinutes = prefs.getInt(K.BIOMETRIC_REMEMBER_MIN, d.biometricRememberMinutes),
            hideFromRecents = prefs.getBoolean(K.HIDE_FROM_RECENTS, d.hideFromRecents),
            appWideBiometricLock = prefs.getBoolean(K.APP_WIDE_LOCK, d.appWideBiometricLock),
            useTrash = prefs.getBoolean(K.USE_TRASH, d.useTrash),
            trashPurgeDays = prefs.getInt(K.TRASH_PURGE_DAYS, d.trashPurgeDays),
            compressImages = prefs.getBoolean(K.COMPRESS_IMAGES, d.compressImages),
            imageQuality = prefs.getInt(K.IMAGE_QUALITY, d.imageQuality),
            doubleTapToEdit = prefs.getBoolean(K.DOUBLE_TAP_EDIT, d.doubleTapToEdit),
            backgroundImagePath = prefs.getString(K.BACKGROUND_IMAGE, null)?.takeIf { it.isNotBlank() },
            backgroundMonochrome = prefs.getBoolean(K.BACKGROUND_MONOCHROME, d.backgroundMonochrome),
            backgroundImageOpacity = prefs.getInt(K.BACKGROUND_IMAGE_OPACITY, (d.backgroundImageOpacity * 100).toInt()) / 100f,
            backgroundFade = prefs.getBoolean(K.BACKGROUND_FADE, d.backgroundFade),
            backgroundFadeOpacity = prefs.getInt(K.BACKGROUND_FADE_OPACITY, (d.backgroundFadeOpacity * 100).toInt()) / 100f,
            topBarOpacity = prefs.getInt(K.TOP_BAR_OPACITY, (d.topBarOpacity * 100).toInt()) / 100f,
            showFirstImage = prefs.getBoolean(K.SHOW_FIRST_IMAGE, d.showFirstImage),
            defaultGalleryLayout = runCatching { GalleryLayout.valueOf(prefs.getString(K.DEFAULT_GALLERY_LAYOUT, null) ?: d.defaultGalleryLayout.name) }.getOrDefault(d.defaultGalleryLayout),
            titleMode = runCatching { TitleMode.valueOf(prefs.getString(K.TITLE_MODE, null) ?: d.titleMode.name) }.getOrDefault(d.titleMode),
            customTitleText = prefs.getString(K.CUSTOM_TITLE_TEXT, d.customTitleText) ?: d.customTitleText,
            rightEdgeSwipeAction = runCatching { RightEdgeSwipeAction.valueOf(prefs.getString(K.RIGHT_EDGE_SWIPE_ACTION, null) ?: d.rightEdgeSwipeAction.name) }.getOrDefault(d.rightEdgeSwipeAction)
        )
    }

    /**
     * Escritura con apply() (asíncrona en background, nunca bloquea el hilo
     * que llama): alcanza con que quede en el mapa en memoria de
     * SharedPreferences para que la próxima lectura sincrónica (read(), de
     * arriba) ya la vea reflejada; no hace falta esperar a que se persista
     * en disco para eso.
     */
    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(K.THEME_MODE, settings.themeMode.name)
            putBoolean(K.DYNAMIC_COLOR, settings.dynamicColor)
            putString(K.SEED_COLOR, settings.seedColorHex)
            putInt(K.GRID_COLUMNS, settings.gridColumns)
            putString(K.NOTE_LAYOUT, settings.noteLayout.name)
            putString(K.FONT_SCALE, settings.fontScale.name)
            putString(K.SORT_ORDER, settings.sortOrder.name)
            putString(K.CHECKBOX_POSITION, settings.checkboxPosition.name)
            putBoolean(K.AUTO_SORT_CHECKED, settings.autoSortChecked)
            putBoolean(K.CONFIRM_DELETE, settings.confirmBeforeDelete)
            putString(K.START_VIEW, settings.startView.name)
            putString(K.LAST_VIEW_MODE, settings.lastViewMode)
            putInt(K.BIOMETRIC_REMEMBER_MIN, settings.biometricRememberMinutes)
            putBoolean(K.HIDE_FROM_RECENTS, settings.hideFromRecents)
            putBoolean(K.APP_WIDE_LOCK, settings.appWideBiometricLock)
            putBoolean(K.USE_TRASH, settings.useTrash)
            putInt(K.TRASH_PURGE_DAYS, settings.trashPurgeDays)
            putBoolean(K.COMPRESS_IMAGES, settings.compressImages)
            putInt(K.IMAGE_QUALITY, settings.imageQuality)
            putBoolean(K.DOUBLE_TAP_EDIT, settings.doubleTapToEdit)
            putString(K.BACKGROUND_IMAGE, settings.backgroundImagePath ?: "")
            putBoolean(K.BACKGROUND_MONOCHROME, settings.backgroundMonochrome)
            putInt(K.BACKGROUND_IMAGE_OPACITY, (settings.backgroundImageOpacity * 100).toInt())
            putBoolean(K.BACKGROUND_FADE, settings.backgroundFade)
            putInt(K.BACKGROUND_FADE_OPACITY, (settings.backgroundFadeOpacity * 100).toInt())
            putInt(K.TOP_BAR_OPACITY, (settings.topBarOpacity * 100).toInt())
            putBoolean(K.SHOW_FIRST_IMAGE, settings.showFirstImage)
            putString(K.DEFAULT_GALLERY_LAYOUT, settings.defaultGalleryLayout.name)
            putString(K.TITLE_MODE, settings.titleMode.name)
            putString(K.CUSTOM_TITLE_TEXT, settings.customTitleText)
            putString(K.RIGHT_EDGE_SWIPE_ACTION, settings.rightEdgeSwipeAction.name)
            apply()
        }
    }
}
