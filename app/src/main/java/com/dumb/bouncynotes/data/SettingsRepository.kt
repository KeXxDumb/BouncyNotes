package com.dumb.bouncynotes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SortOrder { UPDATED, CREATED, ALPHABETICAL, COLOR }
enum class CheckboxPosition { START, END }
enum class StartView { ALL, LAST_USED }
enum class FontScale { SMALL, MEDIUM, LARGE }
enum class NoteLayout { GRID, LIST }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val seedColorHex: String = "#EF6C57",
    val gridColumns: Int = 2,
    val noteLayout: NoteLayout = NoteLayout.GRID,
    val fontScale: FontScale = FontScale.MEDIUM,
    val sortOrder: SortOrder = SortOrder.UPDATED,
    val checkboxPosition: CheckboxPosition = CheckboxPosition.START,
    val autoSortChecked: Boolean = false,
    val confirmBeforeDelete: Boolean = true,
    val startView: StartView = StartView.ALL,
    val lastViewMode: String = "ALL",
    // -1 = hasta cerrar la app, 0 = siempre pedir, >0 = minutos
    val biometricRememberMinutes: Int = -1,
    val hideFromRecents: Boolean = false,
    val appWideBiometricLock: Boolean = false,
    // -1 = nunca purgar
    val useTrash: Boolean = true,
    val trashPurgeDays: Int = 30,
    val compressImages: Boolean = false,
    val imageQuality: Int = 85,
    val doubleTapToEdit: Boolean = true,
    val backgroundImagePath: String? = null,
    val backgroundMonochrome: Boolean = true,
    val backgroundImageOpacity: Float = 1f,
    val backgroundFade: Boolean = true,
    val backgroundFadeOpacity: Float = 0.6f,
    val topBarOpacity: Float = 0.5f
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SEED_COLOR = stringPreferencesKey("seed_color")
        val GRID_COLUMNS = intPreferencesKey("grid_columns")
        val NOTE_LAYOUT = stringPreferencesKey("note_layout")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val CHECKBOX_POSITION = stringPreferencesKey("checkbox_position")
        val AUTO_SORT_CHECKED = booleanPreferencesKey("auto_sort_checked")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val START_VIEW = stringPreferencesKey("start_view")
        val LAST_VIEW_MODE = stringPreferencesKey("last_view_mode")
        val BIOMETRIC_REMEMBER_MIN = intPreferencesKey("biometric_remember_min")
        val HIDE_FROM_RECENTS = booleanPreferencesKey("hide_from_recents")
        val APP_WIDE_LOCK = booleanPreferencesKey("app_wide_lock")
        val USE_TRASH = booleanPreferencesKey("use_trash")
        val TRASH_PURGE_DAYS = intPreferencesKey("trash_purge_days")
        val COMPRESS_IMAGES = booleanPreferencesKey("compress_images")
        val IMAGE_QUALITY = intPreferencesKey("image_quality")
        val DOUBLE_TAP_EDIT = booleanPreferencesKey("double_tap_edit")
        val BACKGROUND_IMAGE = stringPreferencesKey("background_image")
        val BACKGROUND_MONOCHROME = booleanPreferencesKey("background_monochrome")
        val BACKGROUND_FADE = booleanPreferencesKey("background_fade")
        val BACKGROUND_FADE_OPACITY = intPreferencesKey("background_fade_opacity")
        val TOP_BAR_OPACITY = intPreferencesKey("top_bar_opacity")
        val BACKGROUND_IMAGE_OPACITY = intPreferencesKey("background_image_opacity")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            seedColorHex = prefs[Keys.SEED_COLOR] ?: "#EF6C57",
            gridColumns = prefs[Keys.GRID_COLUMNS] ?: 2,
            noteLayout = prefs[Keys.NOTE_LAYOUT]?.let { runCatching { NoteLayout.valueOf(it) }.getOrNull() } ?: NoteLayout.GRID,
            fontScale = runCatching { FontScale.valueOf(prefs[Keys.FONT_SCALE] ?: "MEDIUM") }.getOrDefault(FontScale.MEDIUM),
            sortOrder = runCatching { SortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: "UPDATED") }.getOrDefault(SortOrder.UPDATED),
            checkboxPosition = runCatching { CheckboxPosition.valueOf(prefs[Keys.CHECKBOX_POSITION] ?: "START") }.getOrDefault(CheckboxPosition.START),
            autoSortChecked = prefs[Keys.AUTO_SORT_CHECKED] ?: false,
            confirmBeforeDelete = prefs[Keys.CONFIRM_DELETE] ?: true,
            startView = runCatching { StartView.valueOf(prefs[Keys.START_VIEW] ?: "ALL") }.getOrDefault(StartView.ALL),
            lastViewMode = prefs[Keys.LAST_VIEW_MODE] ?: "ALL",
            biometricRememberMinutes = prefs[Keys.BIOMETRIC_REMEMBER_MIN] ?: -1,
            hideFromRecents = prefs[Keys.HIDE_FROM_RECENTS] ?: false,
            appWideBiometricLock = prefs[Keys.APP_WIDE_LOCK] ?: false,
            useTrash = prefs[Keys.USE_TRASH] ?: true,
            trashPurgeDays = prefs[Keys.TRASH_PURGE_DAYS] ?: 30,
            compressImages = prefs[Keys.COMPRESS_IMAGES] ?: false,
            imageQuality = prefs[Keys.IMAGE_QUALITY] ?: 85,
            doubleTapToEdit = prefs[Keys.DOUBLE_TAP_EDIT] ?: true,
            backgroundImagePath = prefs[Keys.BACKGROUND_IMAGE]?.takeIf { it.isNotBlank() },
            backgroundMonochrome = prefs[Keys.BACKGROUND_MONOCHROME] ?: true,
            backgroundFade = prefs[Keys.BACKGROUND_FADE] ?: true,
            backgroundFadeOpacity = (prefs[Keys.BACKGROUND_FADE_OPACITY] ?: 60) / 100f,
            topBarOpacity = (prefs[Keys.TOP_BAR_OPACITY] ?: 50) / 100f,
            backgroundImageOpacity = (prefs[Keys.BACKGROUND_IMAGE_OPACITY] ?: 100) / 100f
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val updated = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.SEED_COLOR] = updated.seedColorHex
            prefs[Keys.GRID_COLUMNS] = updated.gridColumns
            prefs[Keys.NOTE_LAYOUT] = updated.noteLayout.name
            prefs[Keys.FONT_SCALE] = updated.fontScale.name
            prefs[Keys.SORT_ORDER] = updated.sortOrder.name
            prefs[Keys.CHECKBOX_POSITION] = updated.checkboxPosition.name
            prefs[Keys.AUTO_SORT_CHECKED] = updated.autoSortChecked
            prefs[Keys.CONFIRM_DELETE] = updated.confirmBeforeDelete
            prefs[Keys.START_VIEW] = updated.startView.name
            prefs[Keys.LAST_VIEW_MODE] = updated.lastViewMode
            prefs[Keys.BIOMETRIC_REMEMBER_MIN] = updated.biometricRememberMinutes
            prefs[Keys.HIDE_FROM_RECENTS] = updated.hideFromRecents
            prefs[Keys.APP_WIDE_LOCK] = updated.appWideBiometricLock
            prefs[Keys.USE_TRASH] = updated.useTrash
            prefs[Keys.TRASH_PURGE_DAYS] = updated.trashPurgeDays
            prefs[Keys.COMPRESS_IMAGES] = updated.compressImages
            prefs[Keys.IMAGE_QUALITY] = updated.imageQuality
            prefs[Keys.DOUBLE_TAP_EDIT] = updated.doubleTapToEdit
            prefs[Keys.BACKGROUND_IMAGE] = updated.backgroundImagePath ?: ""
            prefs[Keys.BACKGROUND_MONOCHROME] = updated.backgroundMonochrome
            prefs[Keys.BACKGROUND_FADE] = updated.backgroundFade
            prefs[Keys.BACKGROUND_FADE_OPACITY] = (updated.backgroundFadeOpacity * 100).toInt()
            prefs[Keys.TOP_BAR_OPACITY] = (updated.topBarOpacity * 100).toInt()
            prefs[Keys.BACKGROUND_IMAGE_OPACITY] = (updated.backgroundImageOpacity * 100).toInt()
        }
    }

    suspend fun setLastViewMode(mode: String) {
        context.dataStore.edit { it[Keys.LAST_VIEW_MODE] = mode }
    }
}
