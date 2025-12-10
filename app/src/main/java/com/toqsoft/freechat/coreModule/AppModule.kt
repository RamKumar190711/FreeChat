package com.toqsoft.freechat.coreModule


import android.content.Context
import com.toqsoft.freechat.coreModel.UserPreferencesRepository
import com.toqsoft.freechat.coreNetwork.MqttClientManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMqttManager(): MqttClientManager = MqttClientManager()

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext ctx: Context): UserPreferencesRepository = UserPreferencesRepository(ctx)
}
