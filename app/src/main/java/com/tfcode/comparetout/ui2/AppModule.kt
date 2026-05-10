package com.tfcode.comparetout.ui2

import android.app.Application
import android.content.Context
import android.util.Log
import com.tfcode.comparetout.model.ToutcRepository
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
    fun provideRepository(@ApplicationContext context: Context): ToutcRepository {
        Log.d("UI2", "AppModule.provideRepository — creating ToutcRepository")
        val repo = ToutcRepository(context.applicationContext as Application)
        Log.d("UI2", "AppModule.provideRepository — ToutcRepository created: $repo")
        return repo
    }
}
