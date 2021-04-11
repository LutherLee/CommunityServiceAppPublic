package com.example.communityserviceapp.ui.faq

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.example.communityserviceapp.data.repository.FaqRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FaqViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    faqRepository: FaqRepository,
) : ViewModel() {

    companion object {
        private val faqConfig = PagingConfig(
            prefetchDistance = 5,
            enablePlaceholders = false,
            initialLoadSize = 15,
            pageSize = 3
        )
    }

    val faqList = Pager(faqConfig) { faqRepository.getAllFaqs() }.liveData.cachedIn(viewModelScope)
}
