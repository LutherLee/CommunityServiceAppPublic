package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.BannerDao
import com.example.communityserviceapp.data.model.Banner
import com.example.communityserviceapp.util.attachFirestoreRealTimeListener
import com.example.communityserviceapp.util.getString
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BannerRepository @Inject constructor(
    private val bannerDao: BannerDao,
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        private var bannerRealTimeListener: ListenerRegistration? = null

        fun attachBannerRealTimeListener(
            lastBannerFetchTimestamp: Long,
            bannerDao: BannerDao
        ) {
            if (bannerRealTimeListener == null) {
                Timber.d("lastFetchTimestamp = $lastBannerFetchTimestamp")
                Timber.d("Attaching banner real-time listener")
                bannerRealTimeListener = attachFirestoreRealTimeListener(
                    FirebaseDataType.Firestore.FDTBanner(dao = bannerDao),
                    lastBannerFetchTimestamp
                )
            } else {
                Timber.d("Unable to attach Banner listener")
            }
        }

        fun removeBannerRealTimeListener() {
            if (bannerRealTimeListener != null) {
                bannerRealTimeListener!!.remove()
                bannerRealTimeListener = null
                Timber.d("Banner real time listener removed")
            } else {
                Timber.d("Unable to remove Banner real time listener")
            }
        }
    }

    suspend fun setupBanners() {
        val lastBannerFetchTimestamp =
            sharedPreferences.getLong(getString(R.string.banner_last_fetch_timestamp), 0)
        if (lastBannerFetchTimestamp == 0L) {
            // Value might be 0 due to first time app launch, sharedPreferences has no data yet.
            // Thus, get the highest last updated timestamp from the database
            attachBannerRealTimeListener(bannerDao.getBannerHighestTimestamp(), bannerDao)
        } else {
            attachBannerRealTimeListener(lastBannerFetchTimestamp, bannerDao)
        }
    }

    fun getAllBanners(): Flow<List<Banner>> = bannerDao.getAllBanners()
}
