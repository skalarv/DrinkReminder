package com.drinkreminder.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DrinkLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drinkLogDao(): DrinkLogDao
}
