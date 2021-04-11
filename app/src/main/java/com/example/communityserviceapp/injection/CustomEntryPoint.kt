package com.example.communityserviceapp.injection

import android.content.SharedPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CustomEntryPoint {
    fun sharedPreferences(): SharedPreferences?
}
