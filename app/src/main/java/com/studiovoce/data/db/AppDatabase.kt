package com.studiovoce.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClipEntity::class,
        ClipAnalysisEntity::class,
        ClipProgressEntity::class,
        AttemptEntity::class,
        SessionEntity::class,
        CheckpointEntity::class,
        ExerciseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao
    abstract fun attemptDao(): AttemptDao
    abstract fun sessionDao(): SessionDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        /**
         * Niente `fallbackToDestructiveMigration`: i progressi e le registrazioni
         * non si possono perdere (SPEC §4.1). Ogni versione nuova porta una migrazione.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "studio-fagnani.db")
                .build()
    }
}
