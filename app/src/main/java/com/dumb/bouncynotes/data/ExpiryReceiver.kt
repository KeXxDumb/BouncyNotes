package com.dumb.bouncynotes.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.dumb.bouncynotes.EXPIRE") return
        val noteId = intent.getLongExtra(ExpiryScheduler.EXTRA_NOTE_ID, 0L)
        if (noteId == 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ExpiryManager.purgeNote(context, noteId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
