package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import androidx.paging.PagingSource
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.CrowdFundingDao
import com.example.communityserviceapp.data.model.CrowdFunding
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.setupFirebaseDatabaseReferencePath
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrowdFundingRepository @Inject constructor(
    private val crowdFundingDao: CrowdFundingDao,
    private val sharedPreferences: SharedPreferences
) {
    companion object {
        var crowdFundingRealTimeListener: ValueEventListener? = null
        var crowdFundingDatabaseRef: Query? = null

        fun attachCrowdFundingRealTimeListener(
            lastCrowdFundingFetchTimestamp: Long,
            crowdFundingDao: CrowdFundingDao
        ) {
            if (crowdFundingRealTimeListener == null && crowdFundingDatabaseRef == null) {
                Timber.d("lastCrowdFundingFetchTimestamp = $lastCrowdFundingFetchTimestamp")
                Timber.d("Attaching crowdFunding real-time listener")
                setupFirebaseDatabaseReferencePath(
                    FirebaseDataType.RealTimeDatabase.FDTCrowdFunding(dao = crowdFundingDao),
                    lastCrowdFundingFetchTimestamp
                )
            } else {
                Timber.d("Unable to attach CrowdFunding listener")
            }
        }

        fun removeCrowdFundingRealTimeListener() {
            if (crowdFundingRealTimeListener != null && crowdFundingDatabaseRef != null) {
                crowdFundingDatabaseRef!!.removeEventListener(crowdFundingRealTimeListener!!)
                crowdFundingRealTimeListener = null
                crowdFundingDatabaseRef = null
                Timber.d("CrowdFunding real time listener removed")
            } else {
                Timber.d("Unable to remove CrowdFunding real time listener")
            }
        }
    }

    suspend fun setupCrowdFundings() {
        val lastCrowdFundingFetchTimestamp =
            sharedPreferences.getLong(getString(R.string.crowdFunding_last_fetch_timestamp), 0)
        if (lastCrowdFundingFetchTimestamp == 0L) {
            // Value might be 0 due to first time app launch, sharedPreferences has no data yet.
            // Thus, get the highest last updated timestamp from the database.
            // Since we are using startAt() for real-time database, we add 1 to the highest Timestamp
            attachCrowdFundingRealTimeListener(
                crowdFundingDao.getCrowdFundingHighestTimestamp() + 1,
                crowdFundingDao
            )
        } else {
            attachCrowdFundingRealTimeListener(lastCrowdFundingFetchTimestamp, crowdFundingDao)
        }
    }

    fun getAllCrowdFundings(): PagingSource<Int, CrowdFunding> = crowdFundingDao.getAllCrowdFundings()
}
