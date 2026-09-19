package com.dumb.bouncynotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// exportSchema = true (antes false): sin esto, Room no deja un historial de
// cómo era el esquema en cada versión — y sin ese historial, escribir una
// Migration real el día de mañana (agregar un campo a Note, por ejemplo) es
// mucho más propenso a errores, porque no hay con qué comparar "de dónde
// viene" el esquema. Los JSON quedan en app/schemas/ (ver el bloque kapt en
// app/build.gradle.kts, que le dice a Room A DÓNDE escribirlos).
@Database(entities = [Note::class], version = 6, exportSchema = true)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        fun getInstance(context: Context): NoteDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "notes.db"
                )
                    // ANTES: fallbackToDestructiveMigration() sin más — si en algún
                    // momento se sube `version` de @Database arriba sin escribir una
                    // Migration real, Room no avisa ni pregunta nada: borra la base
                    // de datos entera y la crea de cero. Para el usuario, eso es
                    // abrir la app después de una actualización y que TODAS sus
                    // notas hayan desaparecido, sin aviso.
                    //
                    // fallbackToDestructiveMigrationFrom(dropAllTables, ...versiones)
                    // dice explícitamente "el borrado destructivo está permitido
                    // ÚNICAMENTE viniendo de estas versiones puntuales" — que son las
                    // que ya existen instaladas por ahí, de antes de que este archivo
                    // se pusiera más cuidadoso. Si el día de mañana se sube la
                    // versión a 7 y ese 7 NO está en esta lista, Room deja de callar
                    // y tapar el problema: en vez de borrar todo en silencio, tira una
                    // excepción clara (Room hace que sea imposible ignorar el error) —
                    // que se ve enseguida en una prueba manual o en un log de la Play
                    // Store, mucho antes de que se pueda confundir con un bug "raro"
                    // de pérdida de datos. Ahí es cuando corresponde escribir una
                    // Migration(6, 7) real en vez de sumar el 7 a esta lista.
                    .fallbackToDestructiveMigrationFrom(false, 1, 2, 3, 4, 5, 6)
                    .build().also { INSTANCE = it }
            }
    }
}
