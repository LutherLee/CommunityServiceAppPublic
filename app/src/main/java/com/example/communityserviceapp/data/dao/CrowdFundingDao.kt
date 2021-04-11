package com.example.communityserviceapp.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.communityserviceapp.data.model.CrowdFunding

@Dao
interface CrowdFundingDao : BaseDao<CrowdFunding> {
    @Query("SELECT * FROM crowdFunding")
    fun getAllCrowdFundings(): PagingSource<Int, CrowdFunding>

    @Query("SELECT MAX(updatedAt) FROM crowdFunding LIMIT 1")
    suspend fun getCrowdFundingHighestTimestamp(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrowdFundingList(crowdFundings: List<CrowdFunding>)
}