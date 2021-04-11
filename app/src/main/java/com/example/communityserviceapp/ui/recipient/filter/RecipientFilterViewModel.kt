package com.example.communityserviceapp.ui.recipient.filter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.communityserviceapp.data.repository.RecipientRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecipientFilterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipientRepository: RecipientRepository
) : ViewModel() {

    companion object {
        const val RECIPIENT_FILTER_SQL_QUERY = "recipient_filter_sql_query"

        private val recipientFilterConfig = PagingConfig(
            prefetchDistance = 5,
            enablePlaceholders = true,
            initialLoadSize = 15,
            pageSize = 3
        )
    }

    private val filterRecipientSQLQuery =
        savedStateHandle.getLiveData(RECIPIENT_FILTER_SQL_QUERY, SimpleSQLiteQuery(""))

    val pagedFilteredRecipientItemList = filterRecipientSQLQuery.switchMap { sqlQuery ->
        Pager(recipientFilterConfig) {
            recipientRepository.getFilteredRecipientItemListPagingSource(sqlQuery)
        }.liveData.cachedIn(viewModelScope)
    }

    fun setFilterRecipientSQLQuery(query: String) {
        filterRecipientSQLQuery.value = SimpleSQLiteQuery(query)
    }
}
