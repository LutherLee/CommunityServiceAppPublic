package com.example.communityserviceapp.ui.recipient.favorite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.communityserviceapp.data.repository.FavoriteRepository
import com.example.communityserviceapp.util.DoubleTrigger
import com.example.communityserviceapp.util.deleteFirebaseUserAllFavorites
import com.example.communityserviceapp.util.saveRecipientAsFavoriteOrUnfavourite
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.*
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class RecipientFavoriteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    companion object {
        const val FAVOURITE_CURRENT_USER_ID = "favourite_current_user_id"
        const val FAVOURITE_RECIPIENT_SEARCH = "favourite_recipient_search"

        val recipientFavoritesConfig = PagingConfig(
            enablePlaceholders = true,
            initialLoadSize = 15,
            pageSize = 3,
            prefetchDistance = 3
        )
    }

    val searchFavouriteRecipientSearchKeyword =
        savedStateHandle.getLiveData(FAVOURITE_RECIPIENT_SEARCH, "")

    var currentUserID = savedStateHandle.getLiveData(FAVOURITE_CURRENT_USER_ID, "")

    val userFavoritedRecipients =
        DoubleTrigger(currentUserID, searchFavouriteRecipientSearchKeyword).switchMap {
            val currentUserID = it.first as String
            val searchKeyword = it.second as String

            if (searchKeyword.isEmpty()) {
                Pager(recipientFavoritesConfig) {
                    favoriteRepository.getPagedRecipientsFromFavoriteListBasedOnUserID(
                        currentUserID
                    )
                }.liveData.cachedIn(viewModelScope)
            } else {
                val searchQuery = "SELECT * FROM recipient INNER JOIN favorite ON " +
                    "recipient.id = favorite.recipientID " +
                    "WHERE favorite.userID = \"$currentUserID\" " +
                    "AND recipient.name LIKE \"%$searchKeyword%\" " +
                    "ORDER BY name"
                Pager(recipientFavoritesConfig) {
                    favoriteRepository.getPagedRecipientsFromFavoriteListByQuery(
                        SimpleSQLiteQuery(searchQuery)
                    )
                }.liveData.cachedIn(viewModelScope)
            }
        }

    suspend fun deleteUserAllFavorites(): Boolean {
        val userID = currentUserID.value as String
        if (userID.isEmpty()) {
            return false
        }
        val data = mutableMapOf<String, Any?>()
        data["deleteAll"] = true
        data["updatedAt"] = Timestamp.now().seconds
        return deleteFirebaseUserAllFavorites(userID, data)
    }

    suspend fun setFavoriteOrUnfavouriteRecipient(
        recipientID: String,
        isDeleted: Boolean
    ): Boolean {
        val userID = currentUserID.value as String
        if (userID.isEmpty()) {
            return false
        }
        val data: MutableMap<String, Any?> = HashMap()
        data["userID"] = userID
        data["recipientID"] = recipientID
        data["updatedAt"] = Timestamp.now().seconds
        data["isDeleted"] = isDeleted
        return saveRecipientAsFavoriteOrUnfavourite(data, userID, recipientID)
    }
}
