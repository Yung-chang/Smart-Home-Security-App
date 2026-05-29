package com.smarthome.guardian.di

import com.smarthome.guardian.data.repository.DeviceRepositoryImpl
import com.smarthome.guardian.data.repository.SecurityRepositoryImpl
import com.smarthome.guardian.domain.repository.DeviceRepository
import com.smarthome.guardian.domain.repository.SecurityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceModule {

    @Binds @Singleton
    abstract fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds @Singleton
    abstract fun bindSecurityRepository(impl: SecurityRepositoryImpl): SecurityRepository
}
