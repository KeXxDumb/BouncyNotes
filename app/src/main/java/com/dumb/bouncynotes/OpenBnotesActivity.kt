package com.dumb.bouncynotes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dumb.bouncynotes.data.ExpiryScheduler
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteRepository
import com.dumb.bouncynotes.data.ReminderScheduler
import com.dumb.bouncynotes.data.ShareFileManager
import com.dumb.bouncynotes.ui.theme.NotesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Entry point de "Abrir con BouncyNotes" para archivos .bnotes. Es la única
// Activity exportada además de los aliases del launcher: recibe el Uri,
// descifra, comprueba la fecha límite ANTES de devolver notas y guarda lo que
// corresponda. Nunca muestra el contenido de un archivo caducado.
class OpenBnotesActivity : ComponentActivity() {

    private val status = MutableStateFlow("Abriendo archivo…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        val sharedUri: Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val uri: Uri? = intent.data ?: sharedUri

        lifecycleScope.launch(Dispatchers.IO) {
            if (uri == null) {
                status.value = "No se recibió ningún archivo."
                return@launch
            }

            when (val result = ShareFileManager.read(this@OpenBnotesActivity, uri)) {
                is ShareFileManager.ReadResult.Expired -> {
                    status.value = "Este archivo caducó. No se importó ninguna nota."
                }

                is ShareFileManager.ReadResult.Invalid -> {
                    status.value = "El archivo no es un .bnotes válido o está dañado."
                }

                is ShareFileManager.ReadResult.Success -> {
                    val notes = ShareFileManager.prepareForImport(this@OpenBnotesActivity, result.payload)
                    if (notes.isEmpty()) {
                        status.value = "El archivo no contiene notas."
                    } else {
                        val dao = NoteDatabase.getInstance(this@OpenBnotesActivity).noteDao()
                        val repository = NoteRepository(dao, this@OpenBnotesActivity)
                        notes.forEach { note ->
                            val savedId = repository.save(note)
                            if (note.reminderAt != null) {
                                ReminderScheduler.schedule(this@OpenBnotesActivity, note.copy(id = savedId))
                            }
                            if (note.expiresAt != null) {
                                ExpiryScheduler.schedule(this@OpenBnotesActivity, note.copy(id = savedId))
                            }
                        }
                        val limit = result.payload.expiresAt?.let {
                            " Caducan el " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) + "."
                        } ?: ""
                        status.value = "Se importaron ${notes.size} nota(s).$limit"
                    }
                }
            }
        }

        setContent {
            NotesTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val text by status.collectAsState()
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = text,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            startActivity(
                                Intent(this@OpenBnotesActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        }) { Text("Abrir BouncyNotes") }
                    }
                }
            }
        }
    }
}
