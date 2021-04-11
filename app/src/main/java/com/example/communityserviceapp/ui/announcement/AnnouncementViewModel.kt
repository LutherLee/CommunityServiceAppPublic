package com.example.communityserviceapp.ui.announcement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.example.communityserviceapp.data.repository.AnnouncementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    announcementRepository: AnnouncementRepository,
) : ViewModel() {

    companion object {
        val announcementPagingConfig = PagingConfig(
            enablePlaceholders = false,
            initialLoadSize = 25,
            pageSize = 3,
            prefetchDistance = 3
        )
    }

    val pagedAnnouncementList = Pager(announcementPagingConfig) {
        announcementRepository.getPagedAnnouncements()
    }.liveData.cachedIn(viewModelScope)
}
