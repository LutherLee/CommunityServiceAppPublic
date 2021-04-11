package com.example.communityserviceapp.ui.home

import android.content.SharedPreferences
import androidx.lifecycle.*
import androidx.paging.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.data.repository.RecipientRepository
import com.example.communityserviceapp.data.repository.RecommendationMetadataRepository
import com.example.communityserviceapp.util.currentFirebaseUser
import com.example.communityserviceapp.util.getString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

/**
 * [refreshSuggestionsTrigger] A trigger to refresh the KBF + CBF list
 * [knowledgeBasedFiltering] Knowledge-Based Filtering (KBF) Recommendation list
 * [contentBasedFiltering] Content-Based Filtering (CBF) Recommendation list
 * [recommendationList] KBF + CBF list result
 */
@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipientRepository: RecipientRepository,
    private val recommendationMetadataRepository: RecommendationMetadataRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val refreshSuggestionsTrigger = MutableLiveData<Int>()

    private val knowledgeBasedFiltering =
        refreshSuggestionsTrigger.switchMap { liveData { emit(getKBFResult()) } }

    private val contentBasedFiltering = refreshSuggestionsTrigger.switchMap {
        liveData { emit(getTopFiveMostWeightedJobNatureForCBFCalculation()) }
    }

    val recommendationList =
        knowledgeBasedFiltering.combineWith(contentBasedFiltering) { KBFList, CBFList ->
            return@combineWith if (KBFList != null && CBFList != null) {
                KBFList.plus(CBFList)
            } else {
                listOf()
            }
        }

    private suspend fun getKBFResult(): List<Recipient> {
        val currentUserID = currentFirebaseUser?.uid
        return if (checkIfUserHasSetRecommendationCriteria(currentUserID)) {
            val previousLocationCriteria = getPreviousLocationRecommendationCriteria(currentUserID)
            val previousJobNatureCriteria =
                getPreviousJobNatureRecommendationCriteria(currentUserID)

            // Not Yet setup recommendation criteria
            if (previousJobNatureCriteria.isNullOrEmpty() or previousLocationCriteria.isNullOrEmpty()) {
                return listOf()
            }
            // Already setup recommendation criteria
            if (currentUserID != null) {
                generateKBFRecommendationList(
                    previousJobNatureCriteria!!,
                    previousLocationCriteria!!,
                    currentUserID
                )
            } else {
                generateKBFRecommendationList(
                    previousJobNatureCriteria!!,
                    previousLocationCriteria!!,
                    null
                )
            }
        } else {
            // Not Yet setup recommendation criteria
            return listOf()
        }
    }

    fun getPreviousJobNatureRecommendationCriteria(currentUserID: String?): String? {
        return if (currentUserID != null) {
            sharedPreferences.getString(
                currentUserID + getString(R.string.user_selected_job_nature_recommendation_criteria),
                null
            )
        } else {
            sharedPreferences.getString(
                getString(R.string.user_selected_job_nature_recommendation_criteria),
                null
            )
        }
    }

    fun getPreviousLocationRecommendationCriteria(currentUserID: String?): String? {
        return if (currentUserID != null) {
            sharedPreferences.getString(
                currentUserID + getString(R.string.user_selected_location_recommendation_criteria),
                null
            )
        } else {
            sharedPreferences.getString(
                getString(R.string.user_selected_location_recommendation_criteria),
                null
            )
        }
    }

    private fun checkIfUserHasSetRecommendationCriteria(currentUserID: String?): Boolean {
        return if (currentUserID != null) {
            sharedPreferences.getBoolean(
                currentUserID + getString(R.string.has_set_recommendation_criteria),
                false
            )
        } else {
            sharedPreferences.getBoolean(
                getString(R.string.has_set_recommendation_criteria), false
            )
        }
    }

    private suspend fun generateKBFRecommendationList(
        previousJobNatureCriteria: String,
        previousLocationCriteria: String,
        userID: String?
    ): MutableList<Recipient> {
        val jobNatures = previousJobNatureCriteria.split(", ").toTypedArray()
        val locations = previousLocationCriteria.split(", ").toTypedArray()
        val query = generateKBFSQLQuery(jobNatures, locations, userID)
        return recipientRepository.getRecipientList(query)
    }

    private fun generateKBFSQLQuery(
        jobNatures: Array<String>,
        locations: Array<String>,
        userID: String?
    ): SimpleSQLiteQuery {
        val query = StringBuilder("SELECT * FROM recipient WHERE (")

        // check job nature criteria
        var numOfCriteria = jobNatures.size
        if (numOfCriteria > 0) {
            query.append("jobNature LIKE \"%" + jobNatures[0] + "%\"")
            if (numOfCriteria > 1) {
                // more than one criteria
                for (i in 1 until numOfCriteria) {
                    query.append(" OR jobNature LIKE \"%" + jobNatures[i] + "%\"")
                }
            }
            query.append(") AND (")
            // check location criteria
            numOfCriteria = locations.size
            if (numOfCriteria > 0) {
                query.append("state LIKE \"%" + locations[0] + "%\"")
                if (numOfCriteria > 1) {
                    // more than one criteria
                    for (i in 1 until numOfCriteria) {
                        query.append(" OR state LIKE \"%" + locations[i] + "%\"")
                    }
                }
            }

            // Only return recipient that the user has not viewed before
            query.append(
                ") AND NOT EXISTS (SELECT recipientID from recipientViewedMetadata where " +
                    "recipient.id = recipientViewedMetadata.recipientID "
            )
            if (userID == null || userID.isEmpty()) {
                query.append("AND recipientViewedMetadata.userID = \"defaultUser\")")
            } else {
                query.append("AND recipientViewedMetadata.userID = \"$userID\")")
            }
        }
        query.append(" LIMIT 5")
        return SimpleSQLiteQuery(query.toString())
    }

    private suspend fun getTopFiveMostWeightedJobNatureForCBFCalculation(): List<Recipient> {
        val userID = currentFirebaseUser?.uid
        // get the top 5 heaviest "weighted" job natures (stored in RecommendationMetadata class) associated
        // with the CBF recommendation calculation for a particular user (either login or not yet login)
        val jobNatures = withContext(Dispatchers.IO) {
            getJobNaturesFromRecommendationMetadataList(userID)
        }

        return if (jobNatures != null && jobNatures.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                getCBFRecommendationList(jobNatures, userID, false)
            }
        } else {
            // CBF Cold start problem (user first time app launch and no existing data provided for recommendation
            // calculation). User must at least view one recipient's information for CBF calculation to work properly
            // We could randomly choose 5 job nature to recommend using "ORDER BY RANDOM()" SQL query
            // Note: This method don't do well with large dataset but is okay in this scenario with 21 job nature records
            // See: https://dba.stackexchange.com/questions/955/what-is-the-best-way-to-get-a-random-ordering
            withContext(Dispatchers.IO) {
                val fiveRandomJobNatures =
                    recommendationMetadataRepository.getRandomJobNatures(5)
                if (fiveRandomJobNatures.isEmpty()) {
                    return@withContext listOf()
                }
                getCBFRecommendationList(fiveRandomJobNatures, userID, true)
            }
        }
    }

    private suspend fun getJobNaturesFromRecommendationMetadataList(userID: String?): List<String>? {
        return if (userID != null) {
            recommendationMetadataRepository.getJobNaturesFromRecommendationMetadataList(userID)
        } else {
            recommendationMetadataRepository.getJobNaturesFromRecommendationMetadataList("defaultUser")
        }
    }

    private suspend fun getCBFRecommendationList(
        jobNatures: List<String>,
        userID: String?,
        hasCBFColdStartProblem: Boolean
    ): List<Recipient> {
        val query: StringBuilder
        return if (hasCBFColdStartProblem) {
            // Return recipients that fit any of the 5 job nature specified
            query =
                StringBuilder("SELECT * FROM recipient WHERE jobNature LIKE \"%" + jobNatures[0] + "%\"")
            for (i in 1 until jobNatures.size) {
                query.append(" OR jobNature LIKE \"%" + jobNatures[i] + "%\"")
            }
            query.append(" LIMIT 5")
            recipientRepository.getRecipientList(SimpleSQLiteQuery(query.toString()))
        } else {
            // Return recipients that the user has not viewed that fit any of the 5 job nature
            // specified based on the user's ID
            query =
                StringBuilder("SELECT DISTINCT * FROM recipient WHERE (jobNature LIKE \"%" + jobNatures[0] + "%\"")
            for (i in 1 until jobNatures.size) {
                query.append(" OR jobNature LIKE \"%" + jobNatures[i] + "%\"")
            }
            query.append(
                ") AND NOT EXISTS (SELECT recipientID FROM recipientViewedMetadata where " +
                    "recipient.id = recipientViewedMetadata.recipientID "
            )
            if (userID == null || userID.isEmpty()) {
                query.append("AND recipientViewedMetadata.userID = \"defaultUser\")")
            } else {
                query.append("AND recipientViewedMetadata.userID = \"$userID\")")
            }
            query.append(" LIMIT 5")
            recipientRepository.getRecipientList(SimpleSQLiteQuery(query.toString()))
        }
    }

    suspend fun clearRecommendationMetadata() {
        var currentUserID = currentFirebaseUser?.uid
        if (currentUserID == null) {
            currentUserID = "defaultUser"
        }
        withContext(Dispatchers.IO) {
            recipientRepository.clearRecipientViewedMetadataTable(currentUserID)
            recommendationMetadataRepository.clearRecommendationMetadataTable(currentUserID)
        }
        refreshUserSuggestions()
    }

    fun refreshUserSuggestions() {
        if (refreshSuggestionsTrigger.value == null) {
            refreshSuggestionsTrigger.value = 1
        } else {
            refreshSuggestionsTrigger.value?.let {
                refreshSuggestionsTrigger.value = it + 1
            }
        }
    }

    /**
     * Combine multiple LiveData and emit value
     */
    private fun <T, K, R> LiveData<T>.combineWith(
        liveData: LiveData<K>,
        block: (T?, K?) -> R
    ): LiveData<R> {
        val result = MediatorLiveData<R>()
        result.addSource(this) {
            result.value = block(this.value, liveData.value)
        }
        result.addSource(liveData) {
            result.value = block(this.value, liveData.value)
        }
        return result
    }
}
