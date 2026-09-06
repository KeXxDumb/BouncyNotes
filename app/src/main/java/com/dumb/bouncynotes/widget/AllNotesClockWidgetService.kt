package com.dumb.bouncynotes.widget

import android.content.Intent
import android.widget.RemoteViewsService

class AllNotesClockWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = AllNotesClockWidgetFactory(applicationContext)
}
