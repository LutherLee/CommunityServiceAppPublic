package com.example.communityserviceapp.data.dao

import androidx.paging.DataSource
import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.data.model.RecipientViewedMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao : BaseDao<Recipient> {
    @Query("SELECT * FROM recipient")
    fun getRecipientItemListDataSource(): DataSource.Factory<Int, Recipient>

    @Query("SELECT * FROM recipient")
    fun getAllRecipients(): Flow<List<Recipient>>

    @Query("SELECT * FROM recipient WHERE id = :recipientID")
    suspend fun getRecipientItemByID(recipientID: String): Recipient

    @Query("SELECT * FROM recipient WHERE name LIKE :recipientName LIMIT 1")
    suspend fun getRecipientItemByName(recipientName: String): Recipient

    @Query("SELECT MAX(updatedAt) FROM recipient LIMIT 1")
    suspend fun getRecipientItemHighestTimestamp(): Long

    @RawQuery(observedEntities = [Recipient::class])
    fun getFilteredRecipientItemListPagingSource(query: SupportSQLiteQuery): PagingSource<Int, Recipient>

    @RawQuery(observedEntities = [Recipient::class])
    suspend fun getFilteredRecipientItemList(query: SupportSQLiteQuery): MutableList<Recipient>

    @RawQuery(observedEntities = [Recipient::class])
    fun getPagedSearchResult(query: SupportSQLiteQuery): PagingSource<Int, Recipient>

    @RawQuery(observedEntities = [Recipient::class])
    suspend fun getRecipientList(query: SupportSQLiteQuery): MutableList<Recipient>

    @Query("SELECT * FROM recipient ORDER BY RANDOM() LIMIT 10")
    fun getRandomPagedRecipient(): PagingSource<Int, Recipient>

    @RawQuery(observedEntities = [Recipient::class])
    fun getRecipientSearchSuggestion(query: SupportSQLiteQuery): Flow<List<Recipient>>

    @Query("DELETE FROM recipientViewedMetadata WHERE userID = :userID")
    suspend fun clearRecipientViewedMetadataTable(userID: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipientViewedMetadata(recipientViewedMetadata: RecipientViewedMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipientItemList(recipients: List<Recipient>)
}
