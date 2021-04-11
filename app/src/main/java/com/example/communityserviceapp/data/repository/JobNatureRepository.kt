package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.JobNatureDao
import com.example.communityserviceapp.data.model.JobNature
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.setupFirebaseDatabaseReferencePath
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobNatureRepository @Inject constructor(
    private val jobNatureDao: JobNatureDao,
    private val sharedPreferences: SharedPreferences
) {

    companion object {
        var jobNatureRealTimeListener: ValueEventListener? = null
        var jobNatureDatabaseRef: Query? = null

        fun attachJobNatureRealTimeListener(
            lastJobNatureFetchTimestamp: Long,
            jobNatureDao: JobNatureDao
        ) {
            if (jobNatureRealTimeListener == null && jobNatureDatabaseRef == null) {
                Timber.d("lastJobNatureFetchTimestamp = $lastJobNatureFetchTimestamp")
                Timber.d("Attaching jobNature real-time listener")
                setupFirebaseDatabaseReferencePath(
                    FirebaseDataType.RealTimeDatabase.FDTJobNature(dao = jobNatureDao),
                    lastJobNatureFetchTimestamp
                )
            } else {
                Timber.d("Unable to attach JobNature listener")
            }
        }

        fun removeJobNatureRealTimeListener() {
            if (jobNatureRealTimeListener != null && jobNatureDatabaseRef != null) {
                jobNatureDatabaseRef!!.removeEventListener(jobNatureRealTimeListener!!)
                jobNatureRealTimeListener = null
                jobNatureDatabaseRef = null
                Timber.d("JobNature real time listener removed")
            } else {
                Timber.d("Unable to remove JobNature real time listener")
            }
        }
    }

    suspend fun setupJobNatures() {
        val lastJobNatureFetchTimestamp =
            sharedPreferences.getLong(getString(R.string.jobNature_last_fetch_timestamp), 0)
        if (lastJobNatureFetchTimestamp == 0L) {
            // Value might be 0 due to first time app launch, sharedPreferences has no data yet.
            // Thus, get the highest last updated timestamp from the database.
            // Since we are using startAt() for real-time database, we add 1 to the highest Timestamp
            attachJobNatureRealTimeListener(
                jobNatureDao.getJobNatureHighestTimestamp() + 1,
                jobNatureDao
            )
        } else {
            attachJobNatureRealTimeListener(lastJobNatureFetchTimestamp, jobNatureDao)
        }
    }

    fun getAllJobNature(): LiveData<List<JobNature>> = jobNatureDao.getAllJobNature().asLiveData()
}
