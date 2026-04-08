package com.subwayalert.di

import android.content.Context
import com.subwayalert.data.local.PreferencesManager
import com.subwayalert.data.repository.GeocodingRepository
import com.subwayalert.data.repository.StationRepository
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
    fun providePreferencesManager(
        @ApplicationContext context: Context
    ): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideStationRepository(
        preferencesManager: PreferencesManager
    ): StationRepository {
        return StationRepository(preferencesManager)
    }

    @Provides
    @Singleton
    fun provideGeocodingRepository(
        @ApplicationContext context: Context
    ): GeocodingRepository {
        return GeocodingRepository(context)
    }
}
