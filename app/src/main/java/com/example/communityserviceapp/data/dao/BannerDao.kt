package com.example.communityserviceapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.communityserviceapp.data.model.Banner
import kotlinx.coroutines.flow.Flow

@Dao
interface BannerDao : BaseDao<Banner> {
    @Query("SELECT * FROM banner")
    fun getAllBanners(): Flow<List<Banner>>

    @Query("SELECT MAX(updatedAt) FROM banner LIMIT 1")
    suspend fun getBannerHighestTimestamp(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBannerList(banners: List<Banner>)
}