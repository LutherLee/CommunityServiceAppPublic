package com.example.communityserviceapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.communityserviceapp.data.model.JobNature
import kotlinx.coroutines.flow.Flow

@Dao
interface JobNatureDao : BaseDao<JobNature> {
    @Query("SELECT * FROM jobNature")
    fun getAllJobNature(): Flow<List<JobNature>>

    @Query("SELECT MAX(updatedAt) FROM jobNature LIMIT 1")
    suspend fun getJobNatureHighestTimestamp(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobNatureList(jobNatures: List<JobNature>)
}
