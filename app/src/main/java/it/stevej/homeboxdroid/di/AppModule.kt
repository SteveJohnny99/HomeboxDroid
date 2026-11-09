package it.stevej.homeboxdroid.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Modulo Hilt a livello applicazione.
 * I provider (OkHttp, Retrofit, DataStore, storage sicuro, repository)
 * verranno aggiunti in M1.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
