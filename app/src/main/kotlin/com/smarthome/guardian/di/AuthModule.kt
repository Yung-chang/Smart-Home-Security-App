package com.smarthome.guardian.di

import com.smarthome.guardian.data.repository.AuthRepositoryImpl
import com.smarthome.guardian.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    /** 將 [AuthRepositoryImpl] 綁定至 [AuthRepository] 介面。 */
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
