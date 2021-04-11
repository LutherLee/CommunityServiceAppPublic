package com.example.communityserviceapp.data.dao

import androidx.room.*
import com.example.communityserviceapp.data.model.RecommendationMetadata

@Dao
interface RecommendationMetadataDao {
    @Query(
        "SELECT jobNature.type FROM jobNature " +
            "INNER JOIN recommendationMetadata " +
            "ON jobNature.type = recommendationMetadata.jobNature " +
            "WHERE recommendationMetadata.userID = :userID " +
            "ORDER BY weight DESC LIMIT 5"
    )
    suspend fun getJobNaturesFromRecommendationMetadataList(userID: String): List<String>?

    @Query("SELECT type FROM jobNature ORDER BY RANDOM() LIMIT :number")
    suspend fun getRandomJobNatures(number: Int): List<String>

    @Update
    suspend fun updateRecommendationMetadataList(recommendationMetadata: RecommendationMetadata)

    @Query("DELETE FROM recommendationMetadata WHERE userID = :userID")
    suspend fun clearRecommendationMetadataTable(userID: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecommendationMetadata(recommendationMetadata: RecommendationMetadata): Long
}
