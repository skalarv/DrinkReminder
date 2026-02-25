package com.drinkreminder.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drink_logs")
data class DrinkLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amountMl: Int,
    val timestamp: Long = System.currentTimeMillis()
)
