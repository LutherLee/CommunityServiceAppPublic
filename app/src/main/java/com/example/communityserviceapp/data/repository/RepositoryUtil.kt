package com.example.communityserviceapp.data.repository

import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.*
import com.example.communityserviceapp.data.repository.AnnouncementRepository.Companion.attachAnnouncementRealTimeListener
import com.example.communityserviceapp.data.repository.AnnouncementRepository.Companion.removeAnnouncementRealTimeListener
import com.example.communityserviceapp.data.repository.BannerRepository.Companion.attachBannerRealTimeListener
import com.example.communityserviceapp.data.repository.BannerRepository.Companion.removeBannerRealTimeListener
import com.example.communityserviceapp.data.repository.CrowdFundingRepository.Companion.attachCrowdFundingRealTimeListener
import com.example.communityserviceapp.data.repository.CrowdFundingRepository.Companion.removeCrowdFundingRealTimeListener
import com.example.communityserviceapp.data.repository.FaqRepository.Companion.attachFAQRealTimeListener
import com.example.communityserviceapp.data.repository.FaqRepository.Companion.removeFAQRealTimeListener
import com.example.communityserviceapp.data.repository.FavoriteRepository.Companion.attachUserFavoriteRecipientRealTimeListener
import com.example.communityserviceapp.data.repository.FavoriteRepository.Companion.removeUserFavoriteRecipientRealTimeListener
import com.example.communityserviceapp.data.repository.JobNatureRepository.Companion.attachJobNatureRealTimeListener
import com.example.communityserviceapp.data.repository.JobNatureRepository.Companion.removeJobNatureRealTimeListener
import com.example.communityserviceapp.data.repository.RecipientRepository.Companion.attachRecipientItemRealTimeListener
import com.example.communityserviceapp.data.repository.RecipientRepository.Companion.removeRecipientItemRealTimeListener
import com.example.communityserviceapp.data.repository.SuggestionsRepository.Companion.removeUserRecommendationCriteriaRealTimeListener
import com.example.communityserviceapp.injection.CustomEntryPoint
import com.example.communityserviceapp.ui.MyApplication.Companion.appContext
import com.example.communityserviceapp.util.Constants.FIREBASE_FIELD_UPDATED_AT
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.removeCheckConnectivityListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.firestore.QuerySnapshot
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

var onRecipientReviewLoadedListener: (() -> Unit)? = null

/**
 * Identify whether the data fetched from Firebase should be added or deleted from local DB.
 *
 * All the data fetched's timestamp is greater than the device last recorded fetch timestamp
 * for a particular [FirebaseDataType]
 */
suspend fun <T> identifyDataInconsistencies(
    querySnapshot: QuerySnapshot?,
    dataSnapshot: DataSnapshot?,
    firebaseDataType: FirebaseDataType<T>
) {
    var lastFetchTimestamp: Long = 0
    val dataInsertionList = arrayListOf<T>()
    val dataDeletionList = arrayListOf<T>()

    when (firebaseDataType) {
        is FirebaseDataType.Firestore -> {
            for (snapshot in querySnapshot!!) {
                val itemTimestamp = snapshot.getLong(FIREBASE_FIELD_UPDATED_AT)!!
                Timber.d("document updatedAt = $itemTimestamp")
                if (itemTimestamp > lastFetchTimestamp) {
                    lastFetchTimestamp = itemTimestamp
                }
                if (snapshot["deletedAt"] != null) {
                    dataDeletionList.add(snapshot.toObject(firebaseDataType.associatedClass))
                } else {
                    dataInsertionList.add(snapshot.toObject(firebaseDataType.associatedClass))
                }
            }
            syncDataWithLocalDB(
                lastFetchTimestamp, dataInsertionList,
                dataDeletionList, firebaseDataType
            )
        }
        is FirebaseDataType.RealTimeDatabase -> {
            var shouldSyncDataAutomaticallyWithDefaultImplementation = true
            for (snapshot in dataSnapshot!!.children) {
                val data = snapshot.getValue(firebaseDataType.associatedClass)!!
                val itemTimestamp = snapshot.child(FIREBASE_FIELD_UPDATED_AT).value as Long
                Timber.d("document updatedAt = $itemTimestamp")

                // Check if should delete all items for a table from local db
                if (snapshot.child("deleteAll").exists()) {
                    deleteAllItemsForATableFromLocalDB(firebaseDataType, itemTimestamp)
                    // Disable auto sync using default implementation as we need to delete all items
                    shouldSyncDataAutomaticallyWithDefaultImplementation = false
                    break
                } else {
                    if (itemTimestamp > lastFetchTimestamp) {
                        lastFetchTimestamp = itemTimestamp
                    }
                    // Check if item should be deleted or inserted
                    val isDeletedDataSnapshot = snapshot.child("isDeleted")
                    if (isDeletedDataSnapshot.exists()) {
                        val toDelete = isDeletedDataSnapshot.value as Boolean
                        if (toDelete) {
                            dataDeletionList.add(data)
                        } else {
                            dataInsertionList.add(data)
                        }
                    } else {
                        dataInsertionList.add(data)
                    }
                }
            }
            if (shouldSyncDataAutomaticallyWithDefaultImplementation) {
                syncDataWithLocalDB(
                    lastFetchTimestamp, dataInsertionList,
                    dataDeletionList, firebaseDataType
                )
            }
        }
    }
}

/**
 * Custom data sync function that deletes all records of a table in local DB then reattach listener
 */
private suspend fun <T> deleteAllItemsForATableFromLocalDB(
    firebaseDataType: FirebaseDataType<T>,
    itemTimestamp: Long
) {
    when (val dao = firebaseDataType.dao) {
        is FavoriteDao -> {
            dao.deleteUserAllFavorites()
            Timber.d("Deleting all user favorites")
            updateDataLastFetchTimestamp(
                itemTimestamp, dao,
                firebaseDataType.associatedID, 1
            )
        }
    }
}

/**
 * Execute insertion or deletion of data in local DB based on data inconsistencies identified
 */
private suspend fun <T> syncDataWithLocalDB(
    lastFetchTimestamp: Long,
    dataInsertionList: ArrayList<T>,
    dataDeletionList: ArrayList<T>,
    firebaseDataType: FirebaseDataType<T>
) {
    val dao = firebaseDataType.dao
    val dataName = firebaseDataType.name
    val associatedID = firebaseDataType.associatedID

    dataInsertionList.isNotEmpty().let {
        // try insert, if failed (which means record exist), then update the record
        val insertResult = dao.insertList(dataInsertionList)
        val tempInsertionList = arrayListOf<T>()
        for (i in insertResult.indices) {
            if (insertResult[i] == -1L) tempInsertionList.add(dataInsertionList[i])
        }
        if (tempInsertionList.isNotEmpty()) {
            dao.updateList(tempInsertionList)
        }
    }
    dataDeletionList.isNotEmpty().let { dao.deleteList(dataDeletionList) }

    val numOfInconsistencies = dataInsertionList.size + dataDeletionList.size
    Timber.d("$numOfInconsistencies $dataName inconsistencies")

    updateDataLastFetchTimestamp(lastFetchTimestamp, dao, associatedID, numOfInconsistencies)
}

private fun <T> updateDataLastFetchTimestamp(
    lastFetchTimestamp: Long,
    dao: BaseDao<T>,
    associatedID: String?,
    numOfInconsistencies: Int
) {
    // Get sharedPreferences dependency first using hilt using custom defined entry point
    // since hilt don't support dependencies injection for FirebaseMessagingService class
    // See: https://dagger.dev/hilt/entry-points.html
    val hiltEntryPoint =
        EntryPointAccessors.fromApplication(appContext, CustomEntryPoint::class.java)
    val sharedPreferences = hiltEntryPoint.sharedPreferences()!!

    if (lastFetchTimestamp > 0) {
        when (dao) {
            is BannerDao -> {
                sharedPreferences.edit().putLong(
                    getString(R.string.banner_last_fetch_timestamp), lastFetchTimestamp
                ).apply()
            }
            is FaqDao -> {
                sharedPreferences.edit().putLong(
                    getString(R.string.faq_last_fetch_timestamp), lastFetchTimestamp + 1
                ).apply()
            }
            is FavoriteDao -> {
                sharedPreferences.edit().putLong(
                    associatedID + getString(R.string.favorite_last_fetch_timestamp),
                    lastFetchTimestamp + 1
                ).apply()
            }
            is CrowdFundingDao -> {
                sharedPreferences.edit().putLong(
                    getString(R.string.crowdFunding_last_fetch_timestamp), lastFetchTimestamp + 1
                ).apply()
            }
            is JobNatureDao -> {
                sharedPreferences.edit().putLong(
                    getString(R.string.jobNature_last_fetch_timestamp), lastFetchTimestamp + 1
                ).apply()
            }
            is RecipientDao -> {
                sharedPreferences.edit().putLong(
                    getString(R.string.recipient_item_last_fetch_timestamp), lastFetchTimestamp
                ).apply()
            }
            is ReviewDao -> {
                sharedPreferences.edit().putLong(
                    associatedID + getString(R.string.recipient_review_last_fetch_timestamp),
                    lastFetchTimestamp
                ).apply()
                onRecipientReviewLoadedListener?.invoke()
                return
            }
            is AnnouncementDao -> {
                sharedPreferences.edit().putLong(
                    getString(R.string.announcement_last_fetch_timestamp), lastFetchTimestamp
                ).apply()
            }
        }

        // Check if inconsistencies is >= 1, if true then reattach listener with new lastFetchTimestamp
        // This will help to reduce read operation significantly, if the listener is not removed as
        // new data is added, because the listener's condition (last fetch timestamp) remain the same
        if (numOfInconsistencies >= 1) {
            reattachRealTimeListener(lastFetchTimestamp, dao, associatedID)
        }
    }
}

/**
 * Note: Since we are using startAt() for real-time database, we add 1 to the last fetch timestamp
 */
private fun <T> reattachRealTimeListener(
    highestTimestamp: Long,
    dao: BaseDao<T>,
    associatedID: String?
) {
    when (dao) {
        is BannerDao -> {
            Timber.d("Reattaching Banner Listener")
            removeBannerRealTimeListener()
            attachBannerRealTimeListener(highestTimestamp, dao as BannerDao)
        }
        is FaqDao -> {
            Timber.d("Reattaching Faq Listener")
            removeFAQRealTimeListener()
            attachFAQRealTimeListener(highestTimestamp + 1, dao as FaqDao)
        }
        is FavoriteDao -> {
            Timber.d("Reattaching User Favorite Listener")
            removeUserFavoriteRecipientRealTimeListener()
            attachUserFavoriteRecipientRealTimeListener(
                highestTimestamp + 1,
                associatedID!!, dao as FavoriteDao
            )
        }
        is CrowdFundingDao -> {
            Timber.d("Reattaching Crowd Funding Listener")
            removeCrowdFundingRealTimeListener()
            attachCrowdFundingRealTimeListener(
                highestTimestamp + 1, dao as CrowdFundingDao
            )
        }
        is JobNatureDao -> {
            Timber.d("Reattaching Job Nature Listener")
            removeJobNatureRealTimeListener()
            attachJobNatureRealTimeListener(
                highestTimestamp + 1, dao as JobNatureDao
            )
        }
        is RecipientDao -> {
            Timber.d("Reattaching Recipient Listener")
            removeRecipientItemRealTimeListener()
            attachRecipientItemRealTimeListener(highestTimestamp, dao as RecipientDao)
        }
        is AnnouncementDao -> {
            Timber.d("Reattaching Announcement Listener")
            removeAnnouncementRealTimeListener()
            attachAnnouncementRealTimeListener(highestTimestamp, dao as AnnouncementDao)
        }
    }
}

fun detachAllFirebaseRealTimeListener() {
    removeBannerRealTimeListener()
    removeRecipientItemRealTimeListener()
    removeFAQRealTimeListener()
    removeJobNatureRealTimeListener()
    removeAnnouncementRealTimeListener()
    removeCrowdFundingRealTimeListener()
    removeUserFavoriteRecipientRealTimeListener()
    removeCheckConnectivityListener()
    removeUserRecommendationCriteriaRealTimeListener()
}

fun detachFirebaseRealTimeListenerThatDependsOnUserID() {
    removeUserFavoriteRecipientRealTimeListener()
    removeUserRecommendationCriteriaRealTimeListener()
}
