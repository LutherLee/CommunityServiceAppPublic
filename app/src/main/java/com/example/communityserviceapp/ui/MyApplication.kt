package com.example.communityserviceapp.ui

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.cloudinary.android.MediaManager
import com.example.communityserviceapp.BuildConfig
import com.example.communityserviceapp.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.maps.MapView
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        lateinit var appContext: Context
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        preloadGoogleMap()
        initGoogleSignInClient()
        initCloudinaryAndroid()
        configureTimber()
    }

    // Initialize a dummy google map so that no need to initialize play services
    // later when launching mapFragment causing delay. Decide whether this is needed.
    // See: https://stackoverflow.com/questions/26265526/what-makes-my-map-fragment-loading-slow
    private fun preloadGoogleMap() {
        Thread {
            try {
                MapView(applicationContext).apply {
                    onCreate(null)
                    onPause()
                    onDestroy()
                }
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }.start()
    }

    private fun initGoogleSignInClient() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun configureTimber() {
        if (BuildConfig.DEBUG) Timber.plant(DebugTree())
    }

    override fun getWorkManagerConfiguration() =
        Configuration.Builder().setWorkerFactory(workerFactory).build()

    private fun initCloudinaryAndroid() {
        val config = hashMapOf<String, String>()
        config["cloud_name"] = "triber"
        MediaManager.init(this, config)
    }

    fun getGoogleSignInClient(): GoogleSignInClient = googleSignInClient
}


