package com.example.communityserviceapp.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.communityserviceapp.data.model.Favorite
import com.example.communityserviceapp.data.model.Recipient
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao : BaseDao<Favorite> {
    @Query("SELECT recipientID FROM favorite WHERE userID = :userID")
    fun getRecipientIDsFromFavoriteList(userID: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT * FROM favorite WHERE userID = :userID AND recipientID = :recipientID)")
    suspend fun checkIsFavourited(userID: String, recipientID: String): Boolean

    @Query(
        "SELECT * FROM recipient INNER JOIN favorite ON " +
            "recipient.id = favorite.recipientID " +
            "WHERE favorite.userID = :userID " +
            "ORDER BY name"
    )
    fun getPagedRecipientsFromFavoriteListBasedOnUserID(userID: String): PagingSource<Int, Recipient>

    @RawQuery(observedEntities = [Recipient::class])
    fun getPagedRecipientsFromFavoriteListByQuery(query: SupportSQLiteQuery): PagingSource<Int, Recipient>

    @Query("DELETE FROM favorite")
    suspend fun deleteUserAllFavorites()
}
