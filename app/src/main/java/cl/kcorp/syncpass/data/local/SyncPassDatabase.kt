package cl.kcorp.syncpass.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProyectoEntity::class,
        EventoEntity::class,
        InscripcionEntity::class,
        InscritoEntity::class,
        UsuarioEntity::class,
        PendingActionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SyncPassDatabase : RoomDatabase() {

    abstract fun dao(): SyncPassDao

    companion object {
        @Volatile
        private var instance: SyncPassDatabase? = null

        fun get(context: Context): SyncPassDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SyncPassDatabase::class.java,
                    "syncpass.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
