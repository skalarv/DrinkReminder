package com.drinkreminder.app.di

import android.content.Context
import androidx.room.Room
import com.drinkreminder.app.data.local.AppDatabase
import com.drinkreminder.app.data.local.DrinkLogDao
import com.drinkreminder.app.data.local.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "drink_reminder_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDrinkLogDao(database: AppDatabase): DrinkLogDao {
        return database.drinkLogDao()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }
}
