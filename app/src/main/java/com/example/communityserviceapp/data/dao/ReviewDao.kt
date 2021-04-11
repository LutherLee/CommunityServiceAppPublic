package com.example.communityserviceapp.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.communityserviceapp.data.model.Review

@Dao
interface ReviewDao : BaseDao<Review> {
    @Query("SELECT * FROM review WHERE reviewOn = :recipientID ORDER BY date DESC")
    fun getPagedRecipientReview(recipientID: String): PagingSource<Int, Review>

    @RawQuery(observedEntities = [Review::class])
    fun getFilteredPagedRecipientReview(query: SupportSQLiteQuery): PagingSource<Int, Review>
}
