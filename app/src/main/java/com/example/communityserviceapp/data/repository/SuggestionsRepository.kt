package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import com.example.communityserviceapp.R
import com.example.communityserviceapp.util.Constants
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.realTimeDatabaseInstance
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionsRepository @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val editor: SharedPreferences.Editor
) {

    companion object {
        private var userRecommendationCriteriaRealTimeListener: ValueEventListener? = null
        private var userRecommendationCriteriaDatabaseRef: Query? = null

        fun removeUserRecommendationCriteriaRealTimeListener() {
            if (userRecommendationCriteriaRealTimeListener != null && userRecommendationCriteriaDatabaseRef != null) {
                userRecommendationCriteriaDatabaseRef!!.removeEventListener(
                    userRecommendationCriteriaRealTimeListener!!
                )
                userRecommendationCriteriaRealTimeListener = null
                userRecommendationCriteriaDatabaseRef = null
                Timber.d("User Recommendation Criteria real time listener removed")
            }
        }
    }

    fun setupUserSuggestions(userID: String) {
        if (userID.isNotEmpty()) {
            val lastRecommendationCriteriaFetchTimestamp = sharedPreferences.getLong(
                userID + getString(R.string.recommendation_criteria_last_fetch_timestamp),
                0
            )
            attachUserRecommendationCriteriaRealTimeListener(
                lastRecommendationCriteriaFetchTimestamp,
                userID
            )
        } else {
            Timber.d("Unable to setup user recommendation criteria: User ID is empty")
        }
    }

    private fun attachUserRecommendationCriteriaRealTimeListener(
        lastRecommendationCriteriaFetchTimestamp: Long,
        userID: String
    ) {
        if (userRecommendationCriteriaRealTimeListener == null && userRecommendationCriteriaDatabaseRef == null) {
            Timber.d("lastRecommendationCriteriaFetchTimestamp = $lastRecommendationCriteriaFetchTimestamp")
            Timber.d("Attaching user recommendation criteria real time listener")
            setupUserRecommendationCriteriaPath(
                lastRecommendationCriteriaFetchTimestamp,
                userID
            )
        }
    }

    private fun setupUserRecommendationCriteriaPath(
        lastRecommendationCriteriaFetchTimestamp: Long,
        userID: String
    ) {
        userRecommendationCriteriaDatabaseRef =
            realTimeDatabaseInstance.reference.child("userRecommendationCriteria")
                .child(userID).orderByChild(Constants.FIREBASE_FIELD_UPDATED_AT)
                .limitToLast(1).startAt(lastRecommendationCriteriaFetchTimestamp.toDouble())

        attachRealTimeDatabaseListener(userID)
    }

    private fun attachRealTimeDatabaseListener(userID: String) {
        userRecommendationCriteriaRealTimeListener =
            userRecommendationCriteriaDatabaseRef!!.addValueEventListener(object :
                    ValueEventListener {
                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        // Method called once with the initial value and again whenever
                        // data at this location is updated
                        if (dataSnapshot.exists()) {
                            identifyUserRecommendationInconsistencies(dataSnapshot, userID)
                        } else {
                            Timber.d("No user recommendation criteria cache inconsistencies")
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Timber.w("Failed to attach user recommendation criteria listener")
                    }
                })
    }

    private fun identifyUserRecommendationInconsistencies(value: DataSnapshot, userID: String) {
        var lastFetchTimestamp: Long = 0
        var locationCriteria: String? = null
        var jobNatureCriteria: String? = null
        var counter = 0

        // loop through the snapshot and get the latest updated item only
        for (snapshot in value.children) {
            val itemTimestamp = snapshot.child("updatedAt").value as Long
            Timber.d("document updatedAt = $itemTimestamp")
            if (itemTimestamp > lastFetchTimestamp) {
                lastFetchTimestamp = itemTimestamp
                locationCriteria = snapshot.child("locationCriteria").value as String?
                jobNatureCriteria = snapshot.child("jobNatureCriteria").value as String?
            }
            ++counter
        }
        Timber.d("$counter recommendation criteria inconsistencies")

        if (lastFetchTimestamp > 0 && locationCriteria != null && jobNatureCriteria != null) {
            updateUserRecommendationCriteria(
                locationCriteria,
                jobNatureCriteria,
                userID,
                lastFetchTimestamp
            )
        }
    }

    private fun updateUserRecommendationCriteria(
        locationCriteria: String,
        jobNatureCriteria: String,
        userID: String,
        lastFetchTimestamp: Long
    ) {
        editor.putString(
            userID + getString(R.string.user_selected_job_nature_recommendation_criteria),
            jobNatureCriteria
        ).apply()
        editor.putString(
            userID + getString(R.string.user_selected_location_recommendation_criteria),
            locationCriteria
        ).apply()
        editor.putBoolean(
            userID + getString(R.string.has_set_recommendation_criteria), true
        ).apply()

        // Since we are using startAt() for real-time database, we add 1 to the last fetch Timestamp
        editor.putLong(
            userID + getString(
                R.string.recommendation_criteria_last_fetch_timestamp
            ),
            lastFetchTimestamp
        ).apply()

        removeUserRecommendationCriteriaRealTimeListener()
        attachUserRecommendationCriteriaRealTimeListener(lastFetchTimestamp + 1, userID)
    }
}
