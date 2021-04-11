package com.example.communityserviceapp.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.communityserviceapp.data.model.Faq

@Dao
interface FaqDao : BaseDao<Faq> {
    @Query("SELECT * FROM faq")
    fun getAllFaqs(): PagingSource<Int, Faq>

    @Query("SELECT MAX(updatedAt) FROM faq LIMIT 1")
    suspend fun getFaqHighestTimestamp(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaqList(faqs: List<Faq>)
}