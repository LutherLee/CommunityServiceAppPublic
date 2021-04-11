package com.example.communityserviceapp.data.repository

import com.example.communityserviceapp.data.dao.RecommendationMetadataDao
import com.example.communityserviceapp.data.model.RecommendationMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationMetadataRepository @Inject constructor(private val recommendationMetadataDao: RecommendationMetadataDao) {

    suspend fun getJobNaturesFromRecommendationMetadataList(userID: String): List<String>? =
        recommendationMetadataDao.getJobNaturesFromRecommendationMetadataList(userID)

    suspend fun getRandomJobNatures(number: Int): List<String> =
        recommendationMetadataDao.getRandomJobNatures(number)

    suspend fun updateRecommendationMetadataList(recommendationMetadata: RecommendationMetadata) =
        recommendationMetadataDao.updateRecommendationMetadataList(recommendationMetadata)

    suspend fun insertRecommendationMetadata(recommendationMetadata: RecommendationMetadata): Long =
        recommendationMetadataDao.insertRecommendationMetadata(recommendationMetadata)

    suspend fun clearRecommendationMetadataTable(userID: String) =
        recommendationMetadataDao.clearRecommendationMetadataTable(userID)
}
