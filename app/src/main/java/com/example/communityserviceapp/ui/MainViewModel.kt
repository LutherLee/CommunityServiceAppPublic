package com.example.communityserviceapp.ui

import android.content.SharedPreferences
import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.liveData
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.repository.*
import com.example.communityserviceapp.injection.module.IoDispatcher
import com.example.communityserviceapp.util.attachCheckConnectivityListener
import com.example.communityserviceapp.util.currentFirebaseUser
import com.example.communityserviceapp.util.getString
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * This ViewModel hold the reference to the current (firebase) user and is used to retrieve data
 * and attach firebase listeners that depends on the user id
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @IoDispatcher private val IODispatcher: CoroutineDispatcher,
    private val editor: SharedPreferences.Editor,
    private val recipientRepository: RecipientRepository,
    private val bannerRepository: BannerRepository,
    private val announcementRepository: AnnouncementRepository,
    private val favoriteRepository: FavoriteRepository,
    private val faqRepository: FaqRepository,
    private val jobNatureRepository: JobNatureRepository,
    private val crowdFundingRepository: CrowdFundingRepository,
    private val suggestionsRepository: SuggestionsRepository,
) : ViewModel() {

    companion object {
        private val crowdFundingPagingConfig = PagingConfig(
            initialLoadSize = 2,
            enablePlaceholders = true,
            prefetchDistance = 1,
            pageSize = 1
        )
    }

    /**
     * [firebaseUser] is used to act as a trigger for refreshing user using refreshUser()
     * [currentUser] is used to be observed, which only emit distinct value (based on ID)
     */
    private val firebaseUser = MutableLiveData(currentFirebaseUser)
    val currentUser = firebaseUser.distinctUntilChanged()
    val currentUserID = currentUser.map { it?.uid }

    val bannerList = bannerRepository.getAllBanners().asLiveData()
    val crowdFundingList = Pager(crowdFundingPagingConfig) {
        crowdFundingRepository.getAllCrowdFundings()
    }.liveData.cachedIn(viewModelScope)

    fun attachFirebaseRealTimeListenerThatDependsOnUserID() {
        currentFirebaseUser?.let {
            favoriteRepository.setupUserFavorites(it.uid)
            suggestionsRepository.setupUserSuggestions(it.uid)
        }
    }

    fun attachFirebaseListenerOnAppStart() {
        viewModelScope.launch(IODispatcher) {
            attachCheckConnectivityListener()
            announcementRepository.setupAnnouncements()
            recipientRepository.setupRecipientItems()
            bannerRepository.setupBanners()
            faqRepository.setupFAQs()
            jobNatureRepository.setupJobNatures()
            crowdFundingRepository.setupCrowdFundings()
            attachFirebaseRealTimeListenerThatDependsOnUserID()
        }
    }

    fun refreshUser(className: String) {
        Timber.d("Refreshing User, calling from $className")
        currentFirebaseUser?.let {
            // Manually refreshes the data of the current user from firebase to obtain the latest user data
            it.reload().addOnCompleteListener {
                // Recheck if current user is null
                if (currentFirebaseUser == null) {
                    editor.putBoolean(getString(R.string.is_user_already_login), false).commit()
                    editor.putBoolean(getString(R.string.login_with_google_sign_in), false).commit()
                }
                firebaseUser.value = currentFirebaseUser
            }
            return
        }
        // Code below will be executed when "Firebase Auth User == null"
        // (E.g. When user sign out or user has not sign in yet)
        if (currentFirebaseUser == null) {
            editor.putBoolean(getString(R.string.is_user_already_login), false).commit()
            editor.putBoolean(getString(R.string.login_with_google_sign_in), false).commit()
            firebaseUser.value = null
        }
    }
}
