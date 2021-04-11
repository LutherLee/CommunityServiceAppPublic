package com.example.communityserviceapp.injection.module

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.communityserviceapp.ui.MyApplication.Companion.appContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * General application scope related modules
 */
@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    private lateinit var masterKeyAlias: String
    private lateinit var sharedPreferences: SharedPreferences

    init {
        createEncryptedSharedPreferencesObject()
    }

    @Provides
    fun provideSharedPreferences(): SharedPreferences = sharedPreferences

    @Provides
    fun provideSharedPreferencesEditor(): SharedPreferences.Editor = sharedPreferences.edit()

    private fun createEncryptedSharedPreferencesObject() {
        try {
            masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            sharedPreferences = EncryptedSharedPreferences.create(
                "secret_shared_prefs",
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
