package com.example.communityserviceapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.communityserviceapp.data.dao.*
import com.example.communityserviceapp.data.model.*

/**
 * The Room database for this app
 */
@Database(
    entities = [
        Announcement::class, Recipient::class, Banner::class, CrowdFunding::class,
        Review::class, Faq::class, JobNature::class, Favorite::class,
        RecommendationMetadata::class, RecipientViewedMetadata::class
    ],
    version = 1, exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun announcementDao(): AnnouncementDao
    abstract fun recipientDao(): RecipientDao
    abstract fun bannerDao(): BannerDao
    abstract fun reviewDao(): ReviewDao
    abstract fun faqDao(): FaqDao
    abstract fun crowdFundingDao(): CrowdFundingDao
    abstract fun jobNatureDao(): JobNatureDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun recommendationMetadataDao(): RecommendationMetadataDao
}
