package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.RecipientDao
import com.example.communityserviceapp.data.dao.ReviewDao
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.data.model.RecipientViewedMetadata
import com.example.communityserviceapp.data.model.Review
import com.example.communityserviceapp.util.attachFirestoreRealTimeListener
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.setupFirestoreCollectionGroupPath
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipientRepository @Inject constructor(
    private val recipientDao: RecipientDao,
    private val reviewDao: ReviewDao,
    private val sharedPreferences: SharedPreferences,
) {

    companion object {
        private var recipientItemRealTimeListener: ListenerRegistration? = null

        fun attachRecipientItemRealTimeListener(
            lastRecipientItemFetchTimestamp: Long,
            recipientDao: RecipientDao
        ) {
            if (recipientItemRealTimeListener == null) {
                Timber.d("lastRecipientItemFetchTimestamp = $lastRecipientItemFetchTimestamp")
                Timber.d("Attaching recipient item real-time listener")
                recipientItemRealTimeListener = attachFirestoreRealTimeListener(
                    FirebaseDataType.Firestore.FDTRecipient(dao = recipientDao),
                    lastRecipientItemFetchTimestamp
                )
            } else {
                Timber.d("Unable to attach Recipient item listener")
            }
        }

        fun removeRecipientItemRealTimeListener() {
            if (recipientItemRealTimeListener != null) {
                recipientItemRealTimeListener!!.remove()
                recipientItemRealTimeListener = null
                Timber.d("Recipient item real time listener removed")
            } else {
                Timber.d("Unable to remove Recipient Item real time listener")
            }
        }
    }

    suspend fun setupRecipientItems() {
        val lastRecipientItemFetchTimestamp = sharedPreferences.getLong(
            getString(R.string.recipient_item_last_fetch_timestamp), 0
        )
        if (lastRecipientItemFetchTimestamp == 0L) {
            // Value is 0 may be due to first time app launch, sharedPreferences has no data yet
            // Thus, get the value from the prepolulated database
            attachRecipientItemRealTimeListener(
                recipientDao.getRecipientItemHighestTimestamp(),
                recipientDao
            )
        } else {
            attachRecipientItemRealTimeListener(lastRecipientItemFetchTimestamp, recipientDao)
        }
    }

    fun setupRecipientReviews(recipientID: String) {
        val lastRecipientReviewFetchTimestamp = sharedPreferences.getLong(
            recipientID + getString(R.string.recipient_review_last_fetch_timestamp), 0
        )
        attachRecipientReviewRealTimeListener(
            lastRecipientReviewFetchTimestamp,
            recipientID,
            reviewDao
        )
    }

    private fun attachRecipientReviewRealTimeListener(
        lastRecipientReviewFetchTimestamp: Long,
        recipientID: String,
        reviewDao: ReviewDao
    ) {
        Timber.d("lastRecipientReviewFetchTimestamp = $lastRecipientReviewFetchTimestamp")
        Timber.d("Attaching recipient review real-time listener")
        setupFirestoreCollectionGroupPath(
            FirebaseDataType.Firestore.WithCollectionGroup.FDTReview(
                dao = reviewDao,
                associatedID = recipientID
            ),
            lastRecipientReviewFetchTimestamp
        )
    }

    fun getAllRecipients(): Flow<List<Recipient>> = recipientDao.getAllRecipients()

    fun getPagedSearchResult(sqlQuery: SupportSQLiteQuery): PagingSource<Int, Recipient> =
        recipientDao.getPagedSearchResult(sqlQuery)

    fun getFilteredRecipientItemListPagingSource(sqlQuery: SupportSQLiteQuery): PagingSource<Int, Recipient> {
        return recipientDao.getFilteredRecipientItemListPagingSource(sqlQuery)
    }

    suspend fun getRecipientList(sqlQuery: SupportSQLiteQuery): MutableList<Recipient> {
        return recipientDao.getRecipientList(sqlQuery)
    }

    fun getRecipientSearchSuggestion(query: SupportSQLiteQuery): Flow<List<Recipient>> =
        recipientDao.getRecipientSearchSuggestion(query)

    suspend fun getRecipientItemByID(recipientID: String): Recipient =
        recipientDao.getRecipientItemByID(recipientID)

    suspend fun getRecipientItemByName(recipientName: String): Recipient =
        recipientDao.getRecipientItemByName(recipientName)

    fun getPagedRecipientReview(recipientID: String): PagingSource<Int, Review> =
        reviewDao.getPagedRecipientReview(recipientID)

    fun getFilteredPagedRecipientReview(sqlQuery: SupportSQLiteQuery): PagingSource<Int, Review> =
        reviewDao.getFilteredPagedRecipientReview(sqlQuery)

    suspend fun insertRecipientViewedMetadata(recipientViewedMetadata: RecipientViewedMetadata) =
        recipientDao.insertRecipientViewedMetadata(recipientViewedMetadata)

    suspend fun clearRecipientViewedMetadataTable(userID: String) =
        recipientDao.clearRecipientViewedMetadataTable(userID)
}
