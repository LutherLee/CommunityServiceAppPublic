package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import androidx.paging.PagingSource
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.FaqDao
import com.example.communityserviceapp.data.model.Faq
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.setupFirebaseDatabaseReferencePath
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaqRepository @Inject constructor(
    private val faqDao: FaqDao,
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        var faqRealTimeListener: ValueEventListener? = null
        var faqDatabaseRef: Query? = null

        fun attachFAQRealTimeListener(
            lastFaqFetchTimestamp: Long,
            faqDao: FaqDao
        ) {
            if (faqRealTimeListener == null && faqDatabaseRef == null) {
                Timber.d("lastFaqFetchTimestamp = $lastFaqFetchTimestamp")
                Timber.d("Attaching faq real-time listener")
                setupFirebaseDatabaseReferencePath(
                    FirebaseDataType.RealTimeDatabase.FDTFaq(dao = faqDao),
                    lastFaqFetchTimestamp
                )
            } else {
                Timber.d("Unable to attach Faq listener")
            }
        }

        fun removeFAQRealTimeListener() {
            if (faqRealTimeListener != null && faqDatabaseRef != null) {
                faqDatabaseRef!!.removeEventListener(faqRealTimeListener!!)
                faqRealTimeListener = null
                faqDatabaseRef = null
                Timber.d("FAQ real time listener removed")
            } else {
                Timber.d("Unable to remove Faq real time listener")
            }
        }
    }

    suspend fun setupFAQs() {
        val lastFaqFetchTimestamp =
            sharedPreferences.getLong(getString(R.string.faq_last_fetch_timestamp), 0)
        if (lastFaqFetchTimestamp == 0L) {
            // Value might be 0 due to first time app launch, sharedPreferences has no data yet.
            // Thus, get the highest last updated timestamp from the database.
            // Since we are using startAt() for real-time database, we add 1 to the highest Timestamp
            attachFAQRealTimeListener(faqDao.getFaqHighestTimestamp() + 1, faqDao)
        } else {
            attachFAQRealTimeListener(lastFaqFetchTimestamp, faqDao)
        }
    }

    fun getAllFaqs(): PagingSource<Int, Faq> = faqDao.getAllFaqs()
}
