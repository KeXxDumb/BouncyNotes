package com.dumb.bouncynotes.widget

import android.content.Intent
import android.widget.RemoteViewsService

// Boilerplate mínimo — mismo patrón que PinnedNoteWidgetService/
// LastEditedNoteWidgetService. A diferencia de esos dos, este Factory no
// necesita ningún extra del Intent (no hay una nota fija que mostrar, la
// lista es siempre "todas las notas").
class AllNotesClockWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        AllNotesClockWidgetFactory(applicationContext)
}
