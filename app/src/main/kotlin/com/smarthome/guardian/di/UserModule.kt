package com.smarthome.guardian.di

import com.smarthome.guardian.data.repository.AccessRuleRepositoryImpl
import com.smarthome.guardian.data.repository.UserRepositoryImpl
import com.smarthome.guardian.domain.repository.AccessRuleRepository
import com.smarthome.guardian.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UserModule {

    @Binds @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds @Singleton
    abstract fun bindAccessRuleRepository(impl: AccessRuleRepositoryImpl): AccessRuleRepository
}
