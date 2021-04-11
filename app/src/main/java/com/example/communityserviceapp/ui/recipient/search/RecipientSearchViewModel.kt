package com.example.communityserviceapp.ui.recipient.search

import android.content.res.TypedArray
import androidx.lifecycle.*
import androidx.paging.*
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.repository.JobNatureRepository
import com.example.communityserviceapp.data.repository.RecipientRepository
import com.example.communityserviceapp.util.getTypedArray
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@HiltViewModel
class RecipientSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipientRepository: RecipientRepository,
    val jobNatureRepository: JobNatureRepository
) : ViewModel() {

    companion object {
        const val RECIPIENT_SEARCH_KEYWORD = "recipient_search_query"
        const val DEFAULT_SEARCH_SORTED_RECIPIENT_QUERY = "SELECT * FROM recipient ORDER BY name"
        const val DEFAULT_RECIPIENT_SEARCH_SQLITE_QUERY = "SELECT * FROM recipient WHERE "
        private val searchRecipientPagingConfig = PagingConfig(
            initialLoadSize = 25,
            enablePlaceholders = true,
            prefetchDistance = 10,
            pageSize = 5
        )
        private val searchSuggestionsPagingConfig = PagingConfig(
            initialLoadSize = 25,
            enablePlaceholders = false,
            prefetchDistance = 10,
            pageSize = 5
        )
        private const val defaultSearchSuggestionsQuery = "SELECT * FROM RECIPIENT ORDER BY RANDOM() LIMIT 30"
    }

    val searchKeyword = savedStateHandle.getLiveData(RECIPIENT_SEARCH_KEYWORD, "")

    val searchResult = searchKeyword.switchMap { keyword ->
        Timber.d("keyword = $keyword")
        val sqlQuery = if (keyword.isEmpty()) {
            SimpleSQLiteQuery(DEFAULT_SEARCH_SORTED_RECIPIENT_QUERY)
        } else {
            val tempQuery = DEFAULT_RECIPIENT_SEARCH_SQLITE_QUERY + "name LIKE \"%$keyword%\"" +
                "OR address LIKE \"%$keyword%\" " +
                "OR jobNature LIKE \"%$keyword%\" " +
                "OR state LIKE \"%$keyword%\""
            SimpleSQLiteQuery(tempQuery)
        }
        return@switchMap Pager(searchRecipientPagingConfig) {
            recipientRepository.getPagedSearchResult(sqlQuery)
        }.liveData.cachedIn(viewModelScope)
    }

    // Get 30 random recipient
    // Note: "ORDER BY RANDOM" does not work well with large table as it have to go the whole
    // table before returning random values
    val searchSuggestions = Pager(searchSuggestionsPagingConfig) {
        recipientRepository.getPagedSearchResult(SimpleSQLiteQuery(defaultSearchSuggestionsQuery))
    }.liveData.cachedIn(viewModelScope)

    fun getLocationChips(): Iterator<String?> {
        val statesArrayOfArrays = getTypedArray(R.array.states_array_of_arrays)
        // Put all the states value to linkedHashMap by reading from string resource file
        var stateTypedArray: TypedArray? = null
        val statesMap: LinkedHashMap<String?, String?> = LinkedHashMap()

        for (i in 0 until statesArrayOfArrays.length()) {
            val id = statesArrayOfArrays.getResourceId(i, -1)
            check(id != -1) { "R.array.states_array_of_arrays is not valid" }
            stateTypedArray = getTypedArray(id)
            statesMap[stateTypedArray.getString(1)] = stateTypedArray.getString(0)
        }
        stateTypedArray?.recycle()
        statesArrayOfArrays.recycle()

        // Get all the key of linkedHashMap (Malaysia's state) and inflate chip
        val keys: Set<String?> = statesMap.keys
        return keys.iterator()
    }
}
