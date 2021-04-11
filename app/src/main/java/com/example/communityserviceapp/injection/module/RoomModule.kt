package com.example.communityserviceapp.injection.module

import android.content.Context
import androidx.room.Room
import com.example.communityserviceapp.data.AppDatabase
import com.example.communityserviceapp.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Module specifically for specifying how to supply Room related dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    private const val DATABASE_NAME = "triber-db"

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .createFromAsset("database/$DATABASE_NAME")
            /**
             *  The uncommented code below is to automatically populate local database with
             *  files that is find in "assets" folder of this project, specifically "banners",
             *  "recipients", "jobNatures" and "faqs" using workManager.
             *
             *  Use this when you changed the database structure (schema) or version causing an
             *  error since you also need to update the Database file ("triber-db") in
             *  "assets" > "database" folder. For convenience, instead of modifying the db
             *  file manually, let the app populate the db file using the below uncommented
             *  code and use that db file (from device) to overwrite the one in the project.
             *
             *  Note: Remember to comment attachFirebaseListenerOnAppStart() method in MainViewModel
             *  when doing so.
             */
//            .addCallback(object : RoomDatabase.Callback() {
//                override fun onCreate(db: SupportSQLiteDatabase) {
//                    super.onCreate(db)
//                    // Run async operation and suspend until completed.
//                    CoroutineScope(Dispatchers.IO).launch {
//                        WorkManager.getInstance(context).enqueue(
//                            OneTimeWorkRequestBuilder<DatabaseWorker>().build()
//                        ).await()
//                    }
//                }
//            })
            .build()
    }

    @Singleton
    @Provides
    fun provideAnnouncementDao(appDatabase: AppDatabase): AnnouncementDao {
        return appDatabase.announcementDao()
    }

    @Singleton
    @Provides
    fun provideRecipientDao(appDatabase: AppDatabase): RecipientDao {
        return appDatabase.recipientDao()
    }

    @Singleton
    @Provides
    fun provideCrowdFundingDao(appDatabase: AppDatabase): CrowdFundingDao {
        return appDatabase.crowdFundingDao()
    }

    @Singleton
    @Provides
    fun provideBannerDao(appDatabase: AppDatabase): BannerDao {
        return appDatabase.bannerDao()
    }

    @Singleton
    @Provides
    fun provideCommentDao(appDatabase: AppDatabase): ReviewDao {
        return appDatabase.reviewDao()
    }

    @Singleton
    @Provides
    fun provideFaqDao(appDatabase: AppDatabase): FaqDao {
        return appDatabase.faqDao()
    }

    @Singleton
    @Provides
    fun provideJobNatureDao(appDatabase: AppDatabase): JobNatureDao {
        return appDatabase.jobNatureDao()
    }

    @Singleton
    @Provides
    fun provideFavoriteDao(appDatabase: AppDatabase): FavoriteDao {
        return appDatabase.favoriteDao()
    }

    @Singleton
    @Provides
    fun provideRecommendationMetadataDao(appDatabase: AppDatabase): RecommendationMetadataDao {
        return appDatabase.recommendationMetadataDao()
    }
}
