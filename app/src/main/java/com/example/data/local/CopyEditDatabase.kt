package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        ClipEntity::class,
        TemplateEntity::class,
        ExportedVideoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CopyEditDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun clipDao(): ClipDao
    abstract fun templateDao(): TemplateDao
    abstract fun exportedVideoDao(): ExportedVideoDao

    companion object {
        @Volatile
        private var INSTANCE: CopyEditDatabase? = null

        fun getInstance(context: Context): CopyEditDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CopyEditDatabase::class.java,
                    "copy_edit_db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
