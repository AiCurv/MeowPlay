package com.meowplay.tv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "meowplay_database")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
