package com.example.communityserviceapp.util

import androidx.lifecycle.LiveData

/**
 * A LiveData class that has 'null' value.
 * Note: MutableLiveData(null) would also have the same effect
 *
 * Use case: Return an empty liveData object for condition statement in Transformations.switchMap()
 */
class AbsentLiveData<T> private constructor() : LiveData<T>() {
    init {
        postValue(null)
    }

    companion object {
        fun <T> create(): LiveData<T> {
            return AbsentLiveData()
        }
    }
}