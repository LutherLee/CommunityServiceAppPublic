package com.example.communityserviceapp.ui.recipient

import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.data.model.RecipientViewedMetadata
import com.example.communityserviceapp.data.model.RecommendationMetadata
import com.example.communityserviceapp.data.repository.FavoriteRepository
import com.example.communityserviceapp.data.repository.RecipientRepository
import com.example.communityserviceapp.data.repository.RecommendationMetadataRepository
import com.example.communityserviceapp.injection.module.IoDispatcher
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_EMPTY_DEFAULT
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_VALID
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject

@HiltViewModel
class RecipientViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @IoDispatcher private val IODispatcher: CoroutineDispatcher,
    private val recipientRepository: RecipientRepository,
    private val recommendationMetadataRepository: RecommendationMetadataRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    companion object {
        const val RECIPIENT_CURRENT_RECIPIENT = "recipient_current_recipient"
        const val RECIPIENT_REVIEW = "recipient_review"
        const val RECIPIENT_LOADING_PROGRESS_BAR = "recipient_loading_progress_bar"
        const val RECIPIENT_REVIEW_FILTER_QUERY = "recipient_review_filter_query"

        private val recipientReviewConfig = PagingConfig(
            prefetchDistance = 5,
            enablePlaceholders = false,
            initialLoadSize = 15,
            pageSize = 5
        )
    }

    val currentRecipient = savedStateHandle.getLiveData(RECIPIENT_CURRENT_RECIPIENT, Recipient())
    val recipientReview = savedStateHandle.getLiveData(RECIPIENT_REVIEW, "")
    val enableLoadingProgressBar =
        savedStateHandle.getLiveData(RECIPIENT_LOADING_PROGRESS_BAR, false)

    var sortingOrder = Constants.SORT_ORDER_NEWEST

    private val reviewRefreshTrigger = MutableLiveData<Int>()

    val recipientReviewSearchQuery = savedStateHandle.getLiveData(RECIPIENT_REVIEW_FILTER_QUERY, "")

    val pagedRecipientReviewList = reviewRefreshTrigger.switchMap {
        val searchQuery = recipientReviewSearchQuery.value!!
        if (searchQuery.isEmpty()) {
            val recipientID = currentRecipient.value?.recipientID!!
            Pager(recipientReviewConfig) {
                recipientRepository.getPagedRecipientReview(recipientID)
            }.liveData.cachedIn(viewModelScope)
        } else {
            // When user perform any filter
            val searchQueryWithSortingOrder = searchQuery.appendSortingOrder()
            Pager(recipientReviewConfig) {
                recipientRepository.getFilteredPagedRecipientReview(
                    SimpleSQLiteQuery(
                        searchQueryWithSortingOrder
                    )
                )
            }.liveData.cachedIn(viewModelScope)
        }
    }

    private fun String.appendSortingOrder(): String {
        return when (sortingOrder) {
            Constants.SORT_ORDER_NEWEST -> {
                "$this ORDER BY date DESC"
            }
            Constants.SORT_ORDER_OLDEST -> {
                "$this ORDER BY date ASC"
            }
            else -> {
                "$this ORDER BY date DESC"
            }
        }
    }

    suspend fun saveRecipientAsFavoriteOrUnfavorite(isUnfavorited: Boolean): Boolean {
        val currentUserID = currentFirebaseUser!!.uid
        val currentRecipientID = currentRecipient.value?.recipientID
        val data: MutableMap<String, Any?> = HashMap()
        data["userID"] = currentUserID
        data["recipientID"] = currentRecipientID
        data["updatedAt"] = Timestamp.now().seconds
        data["isDeleted"] = isUnfavorited
        return saveRecipientAsFavoriteOrUnfavourite(data, currentUserID, currentRecipientID!!)
    }

    suspend fun updateRecipientReviewInFirebase(
        userRating: Float,
        selectedRecipientTags: String,
        currentUserID: String,
        recipientID: String
    ): Result<Any> {
        val recipientReviewData = mutableMapOf<String, Any?>()
        val currentUser = currentFirebaseUser!!
        recipientReviewData["reviewerName"] = currentUser.displayName
        recipientReviewData["reviewerID"] = currentUser.uid
        recipientReviewData["reviewOn"] = recipientID
        recipientReviewData["reviewDate"] = Timestamp.now()
        recipientReviewData["reviewTags"] = selectedRecipientTags
        recipientReviewData["reviewMessage"] = recipientReview.value!!.trim()
        recipientReviewData["reviewRating"] = userRating
        recipientReviewData["updatedAt"] = Timestamp.now().seconds
        val userPhotoUrl = currentUser.photoUrl
        if (userPhotoUrl != null) {
            recipientReviewData["reviewerImageUrl"] = userPhotoUrl.toString()
        }
        return updateRecipientReview(recipientReviewData, recipientID, userRating, currentUserID)
    }

    fun insertORUpdateRecommendationMetadata(
        userID: String?,
        jobNatures: Array<String>,
        newWeight: Int
    ) {
        var currentUserID = userID
        if (currentUserID.isNullOrEmpty()) {
            currentUserID = "defaultUser"
        }
        // Get the recipient's job nature and increase the weight of each one by 1
        for (jobNature in jobNatures) {
            val metadata = RecommendationMetadata(currentUserID, jobNature, newWeight)
            viewModelScope.launch {
                if (recommendationMetadataRepository.insertRecommendationMetadata(metadata) == -1L) {
                    // insert failed (which means record exist), then update the record
                    recommendationMetadataRepository.updateRecommendationMetadataList(
                        RecommendationMetadata(currentUserID, jobNature, newWeight)
                    )
                }
            }
        }
    }

    fun updateRecommendationMetadata(recipient: Recipient) {
        // Get the recipient's job nature and increase the weight of each one by 2
        // Note: After the showRateRecipientAlertDialog() method finishes as user submitted rating,
        // updateRecommendationMetadata() will be invoked again in "RecipientInformationFragment"
        // as the view is created again which increase the weight of recipient's job natures by 1.
        val jobNature = recipient.recipientJobNature.split(", ").toTypedArray()
        insertORUpdateRecommendationMetadata(currentFirebaseUser!!.uid, jobNature, 2)
    }

    fun updateUserHasViewedRecipient(userID: String?, recipientID: String) {
        val metadata = if (userID.isNullOrEmpty()) {
            RecipientViewedMetadata("defaultUser", recipientID)
        } else {
            RecipientViewedMetadata(userID, recipientID)
        }
        viewModelScope.launch(IODispatcher) {
            recipientRepository.insertRecipientViewedMetadata(metadata)
        }
    }

    suspend fun checkRecipientIsFavorited(userID: String, recipientID: String): Boolean =
        favoriteRepository.checkIsFavorited(userID, recipientID)

    fun refreshRecipientDetails(recipientID: String) {
        viewModelScope.launch {
            val refreshedRecipientDetails = withContext(IODispatcher) {
                recipientRepository.getRecipientItemByID(recipientID)
            }
            currentRecipient.value = refreshedRecipientDetails
        }
    }

    suspend fun getRecipientItemByID(recipientID: String): Recipient {
        return recipientRepository.getRecipientItemByID(recipientID)
    }

    fun attachRecipientReviewRealTimeListener(recipientID: String) {
        viewModelScope.launch {
            recipientRepository.setupRecipientReviews(recipientID)
        }
    }

    fun isRecipientReviewValid(): Int {
        val value = recipientReview.value!!
        return when {
            value.isEmpty() -> {
                VALUE_STATUS_EMPTY_DEFAULT
            }
            (value.trim().length < 50) or (value.trim().length > 500) -> {
                VALUE_STATUS_INVALID
            }
            else -> {
                VALUE_STATUS_VALID
            }
        }
    }

    fun setRecipient(recipient: Recipient) {
        this.currentRecipient.value = recipient
    }

    fun refreshRecipientReview() {
        if (reviewRefreshTrigger.value == null) {
            reviewRefreshTrigger.postValue(1)
        } else {
            reviewRefreshTrigger.value?.let {
                reviewRefreshTrigger.postValue(it + 1)
            }
        }
    }
}
