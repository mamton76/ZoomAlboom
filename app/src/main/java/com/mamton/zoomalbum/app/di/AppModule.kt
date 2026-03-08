package com.mamton.zoomalbum.app.di

import android.content.Context
import androidx.room.Room
import com.mamton.zoomalbum.data.local.room.AlbumDao
import com.mamton.zoomalbum.data.local.room.AppDatabase
import com.mamton.zoomalbum.data.repository.MediaRepositoryImpl
import com.mamton.zoomalbum.data.repository.ProjectRepositoryImpl
import com.mamton.zoomalbum.domain.repository.MediaRepository
import com.mamton.zoomalbum.domain.repository.ProjectRepository
import dagger.Binds
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
        Room.databaseBuilder(context, AppDatabase::class.java, "zoom_album.db").build()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository
}
