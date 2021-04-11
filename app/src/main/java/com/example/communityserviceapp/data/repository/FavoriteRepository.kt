package com.example.communityserviceapp.data.repository

import android.content.SharedPreferences
import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.FirebaseDataType
import com.example.communityserviceapp.data.dao.FavoriteDao
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.util.getString
import com.example.communityserviceapp.util.setupFirebaseDatabaseReferencePath
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val sharedPreferences: SharedPreferences,
) {

    companion object {
        var userFavoriteRecipientRealTimeListener: ValueEventListener? = null
        var userFavoriteRecipientDatabaseRef: Query? = null

        fun attachUserFavoriteRecipientRealTimeListener(
            lastFavoriteFetchTimestamp: Long,
            userID: String,
            favoriteDao: FavoriteDao
        ) {
            if (userFavoriteRecipientRealTimeListener == null && userFavoriteRecipientDatabaseRef == null) {
                Timber.d("lastFavoriteFetchTimestamp = $lastFavoriteFetchTimestamp")
                Timber.d("Attaching user favorite recipient real time listener")
                setupFirebaseDatabaseReferencePath(
                    FirebaseDataType.RealTimeDatabase
                        .FDTFavorite(dao = favoriteDao, associatedID = userID),
                    lastFavoriteFetchTimestamp
                )
            } else {
                Timber.d("Unable to attach User Favorite Recipient listener")
            }
        }

        fun removeUserFavoriteRecipientRealTimeListener() {
            if (userFavoriteRecipientRealTimeListener != null && userFavoriteRecipientDatabaseRef != null) {
                userFavoriteRecipientDatabaseRef!!.removeEventListener(
                    userFavoriteRecipientRealTimeListener!!
                )
                userFavoriteRecipientRealTimeListener = null
                userFavoriteRecipientDatabaseRef = null
                Timber.d("User Favorite Recipient real time listener removed")
            } else {
                Timber.d("Unable to remove User Favorite Recipient real time listener")
            }
        }
    }

    fun setupUserFavorites(userID: String) {
        if (userID.isNotEmpty()) {
            val lastFavoriteFetchTimestamp = sharedPreferences.getLong(
                userID + getString(R.string.favorite_last_fetch_timestamp), 0
            )
            Timber.d("lastFavoriteFetchTimestamp = $lastFavoriteFetchTimestamp")
            attachUserFavoriteRecipientRealTimeListener(
                lastFavoriteFetchTimestamp,
                userID,
                favoriteDao
            )
        } else {
            Timber.d("Unable to setup user favorites: User ID is empty")
        }
    }

    suspend fun checkIsFavorited(userID: String, recipientID: String): Boolean =
        favoriteDao.checkIsFavourited(userID, recipientID)

    fun getPagedRecipientsFromFavoriteListBasedOnUserID(userID: String): PagingSource<Int, Recipient> =
        favoriteDao.getPagedRecipientsFromFavoriteListBasedOnUserID(userID)

    fun getPagedRecipientsFromFavoriteListByQuery(query: SupportSQLiteQuery): PagingSource<Int, Recipient> =
        favoriteDao.getPagedRecipientsFromFavoriteListByQuery(query)
}
