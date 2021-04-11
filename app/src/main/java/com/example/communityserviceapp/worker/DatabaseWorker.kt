package com.example.communityserviceapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.communityserviceapp.data.dao.*
import com.example.communityserviceapp.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStreamReader

/**
 * A worker class that is used to populate the local database with
 * "banners.json", "faqs.json", "jobNatures.json", "recipients.json"
 * file in assets folder using GSON library
 */
@HiltWorker
class DatabaseWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val bannerDao: BannerDao,
    private val crowdFundingDao: CrowdFundingDao,
    private val faqDao: FaqDao,
    private val recipientDao: RecipientDao,
    private val jobNatureDao: JobNatureDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val BANNER_DATA_FILENAME = "banners.json"
        const val FAQs_DATA_FILENAME = "faqs.json"
        const val JOB_NATURE_DATA_FILENAME = "jobNatures.json"
        const val RECIPIENT_DATA_FILENAME = "recipients.json"
        const val CROWD_FUNDING_DATA_FILENAME = "crowdFundings.json"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("Populating Database...")
            populateJobNatures()
            populateBanners()
            populateCrowdFundings()
            populateFaqs()
            populateRecipients()
            Timber.d("Finished Populating Database...")
            Result.success()
        } catch (error: Throwable) {
            Result.failure()
        }
    }

    private suspend fun populateBanners() {
        Timber.d("Populating Banners")
        val input = applicationContext.assets.open(BANNER_DATA_FILENAME)
        val reader = JsonReader(InputStreamReader(input))
        val type = object : TypeToken<List<Banner>>() {}.type
        val bannerList = Gson().fromJson<List<Banner>>(reader, type)
        input.close()
        bannerDao.insertBannerList(bannerList)
        Timber.d("Finished Populating Banners")
    }

    private suspend fun populateCrowdFundings() {
        Timber.d("Populating CrowdFundings")
        val input = applicationContext.assets.open(CROWD_FUNDING_DATA_FILENAME)
        val reader = JsonReader(InputStreamReader(input))
        val type = object : TypeToken<List<CrowdFunding>>() {}.type
        val crowdFundingList = Gson().fromJson<List<CrowdFunding>>(reader, type)
        input.close()
        crowdFundingDao.insertCrowdFundingList(crowdFundingList)
        Timber.d("Finished Populating CrowdFundings")
    }

    private suspend fun populateFaqs() {
        Timber.d("Populating Faqs")
        val input = applicationContext.assets.open(FAQs_DATA_FILENAME)
        val reader = JsonReader(InputStreamReader(input))
        val type = object : TypeToken<List<Faq>>() {}.type
        val faqList = Gson().fromJson<List<Faq>>(reader, type)
        input.close()
        faqDao.insertFaqList(faqList)
        Timber.d("Finished Populating Faqs")
    }

    private suspend fun populateRecipients() {
        Timber.d("Populating Recipients")
        val input = applicationContext.assets.open(RECIPIENT_DATA_FILENAME)
        val reader = JsonReader(InputStreamReader(input))
        val type = object : TypeToken<List<Recipient>>() {}.type
        val recipientList = Gson().fromJson<List<Recipient>>(reader, type)
        input.close()
        recipientDao.insertRecipientItemList(recipientList)
        Timber.d("Finished Populating Recipients")
    }

    private suspend fun populateJobNatures() {
        Timber.d("Populating Job Natures")
        val input = applicationContext.assets.open(JOB_NATURE_DATA_FILENAME)
        val reader = JsonReader(InputStreamReader(input))
        val type = object : TypeToken<List<JobNature>>() {}.type
        val jobNatureList = Gson().fromJson<List<JobNature>>(reader, type)
        input.close()
        jobNatureDao.insertJobNatureList(jobNatureList)
        Timber.d("Finished Populating Job Natures")
    }
}
