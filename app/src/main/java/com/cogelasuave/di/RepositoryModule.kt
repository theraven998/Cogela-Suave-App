package com.cogelasuave.di

import com.cogelasuave.data.local.RoomTransactionRunner
import com.cogelasuave.data.local.TransactionRunner
import com.cogelasuave.data.repository.SettingsRepositoryImpl
import com.cogelasuave.data.repository.StatsRepositoryImpl
import com.cogelasuave.data.repository.WatchedAppRepositoryImpl
import com.cogelasuave.data.system.SystemDateProvider
import com.cogelasuave.domain.repository.SettingsRepository
import com.cogelasuave.domain.repository.StatsRepository
import com.cogelasuave.domain.repository.WatchedAppRepository
import com.cogelasuave.domain.util.DateProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindWatchedAppRepository(impl: WatchedAppRepositoryImpl): WatchedAppRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    abstract fun bindDateProvider(impl: SystemDateProvider): DateProvider

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
