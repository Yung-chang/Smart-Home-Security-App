package com.smarthome.guardian.di

import com.smarthome.guardian.data.repository.AuditRepositoryImpl
import com.smarthome.guardian.domain.repository.AuditRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuditModule {

    @Binds
    @Singleton
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository
}
