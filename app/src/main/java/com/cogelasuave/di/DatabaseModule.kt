package com.cogelasuave.di

import android.content.Context
import androidx.room.Room
import com.cogelasuave.data.local.AppDatabase
import com.cogelasuave.data.local.dao.DailyStatDao
import com.cogelasuave.data.local.dao.ReasonStatDao
import com.cogelasuave.data.local.dao.SettingsDao
import com.cogelasuave.data.local.dao.WatchedAppDao
import com.cogelasuave.service.SnoozeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideWatchedAppDao(db: AppDatabase): WatchedAppDao = db.watchedAppDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideDailyStatDao(db: AppDatabase): DailyStatDao = db.dailyStatDao()

    @Provides
    fun provideReasonStatDao(db: AppDatabase): ReasonStatDao = db.reasonStatDao()

    @Provides
    @Singleton
    fun provideSnoozeManager(@ApplicationContext context: Context): SnoozeManager =
        SnoozeManager(context)
}
