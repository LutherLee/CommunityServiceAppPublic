package com.example.communityserviceapp.ui.setRecommendationCriteria

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.communityserviceapp.data.repository.JobNatureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SetRecommendationCriteriaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    jobNatureRepository: JobNatureRepository
) : ViewModel() {

    val allRecipientJobNatures = jobNatureRepository.getAllJobNature()
}
