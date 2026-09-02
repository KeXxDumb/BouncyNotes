package com.dumb.bouncynotes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService

class PinnedNoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        return PinnedNoteWidgetFactory(applicationContext, noteId, widgetId)
    }
}
