package com.example.communityserviceapp.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData

/**
 * This class is equivalent to Transformations.switchMap() except that it listen to two
 * livedata value change simultaneously
 *
 * @see: https://stackoverflow.com/questions/49493772/mediatorlivedata-or-switchmap-transformation-with-multiple-parameters
 */
class DoubleTrigger<A, B>(a: LiveData<A>, b: LiveData<B>) : MediatorLiveData<Pair<A?, B?>>() {
    init {
        addSource(a) { value = it to b.value }
        addSource(b) { value = a.value to it }
    }
}