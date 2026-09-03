package com.dumb.bouncynotes.widget

import android.content.Intent
import android.widget.RemoteViewsService

class LastEditedNoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        LastEditedNoteWidgetFactory(applicationContext)
}
