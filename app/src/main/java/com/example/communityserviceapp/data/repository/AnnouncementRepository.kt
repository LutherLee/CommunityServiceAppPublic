package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import androidx.paging.PagingSource
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.AnnouncementDao
import com.example.communityserviceapp.data.model.Announcement
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.setupFirestoreCollectionGroupPath
import com.google.firebase.firestore.ListenerRegistration
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementRepository @Inject constructor(
    private val announcementDao: AnnouncementDao,
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        var announcementRealTimeListener: ListenerRegistration? = null

        fun attachAnnouncementRealTimeListener(
            lastAnnouncementFetchTimestamp: Long,
            announcementDao: AnnouncementDao
        ) {
            if (announcementRealTimeListener == null) {
                Timber.d("lastAnnouncementFetchTimestamp = $lastAnnouncementFetchTimestamp")
                Timber.d("Attaching Announcement real-time listener")
                setupFirestoreCollectionGroupPath(
                    FirebaseDataType.Firestore.WithCollectionGroup.FDTAnnouncement(
                        dao = announcementDao
                    ),
                    lastAnnouncementFetchTimestamp
                )
            } else {
                Timber.d("Unable to attach Announcement listener")
            }
        }

        fun removeAnnouncementRealTimeListener() {
            if (announcementRealTimeListener != null) {
                announcementRealTimeListener!!.remove()
                announcementRealTimeListener = null
                Timber.d("Announcement real time listener removed")
            }
        }
    }

    fun setupAnnouncements() {
        val lastAnnouncementFetchTimestamp = sharedPreferences.getLong(
            getString(R.string.announcement_last_fetch_timestamp), 0
        )

        attachAnnouncementRealTimeListener(lastAnnouncementFetchTimestamp, announcementDao)
    }

    fun getPagedAnnouncements(): PagingSource<Int, Announcement> =
        announcementDao.getPagedAnnouncements()
}
