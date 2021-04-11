package com.example.communityserviceapp.ui.map

import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.data.repository.RecipientRepository
import com.example.communityserviceapp.util.AbsentLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.*
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipientRepository: RecipientRepository
) : ViewModel() {

    companion object {
        const val MAP_RECIPIENT_SEARCH_KEYWORD = "map_recipient_search_keyword"
    }

    val allRecipient = recipientRepository.getAllRecipients().asLiveData()

    private val searchKeyword = savedStateHandle.getLiveData(MAP_RECIPIENT_SEARCH_KEYWORD, "")

    val searchResult = searchKeyword.switchMap { keyword ->
        if (keyword.isEmpty()) {
            AbsentLiveData.create()
        } else {
            val query = SimpleSQLiteQuery(
                "SELECT * FROM recipient WHERE " +
                    "name LIKE \"%$keyword%\"" +
                    "OR address LIKE \"%$keyword%\" " +
                    "OR jobNature LIKE \"%$keyword%\" " +
                    "OR state LIKE \"%$keyword%\" LIMIT 8"
            )
            recipientRepository.getRecipientSearchSuggestion(query).asLiveData()
        }
    }

    suspend fun getRecipientItemByName(recipientName: String): Recipient =
        recipientRepository.getRecipientItemByName(recipientName)

    fun getAllRecipientCountSize(): Int = allRecipient.value!!.size

    fun setSearchKeyword(keyword: String) {
        searchKeyword.value = keyword
    }

    fun refreshSearch() {
        val currentSearchKeyword = searchKeyword.value as String
        if (currentSearchKeyword.isNotEmpty()) { searchKeyword.value = currentSearchKeyword }
    }

    fun reverseStringArray(array: Array<String?>, arraySize: Int): Array<String?> {
        val reversedArray = arrayOfNulls<String>(arraySize)
        var j = arraySize
        for (i in 0 until arraySize) {
            reversedArray[j - 1] = array[i]
            j -= 1
        }
        return reversedArray
    }

    fun sortMapByValue(unsortMap: MutableMap<String, Double>): MutableMap<String, Double> {
        // Convert Map to List of Map
        val list: List<Map.Entry<String, Double>> = LinkedList(unsortMap.entries)

        // Sort list with Collections.sort(), provide a custom Comparator
        Collections.sort(list) { o1: Map.Entry<String, Double>, o2: Map.Entry<String, Double> ->
            o1.value.compareTo(
                o2.value
            )
        }

        // Loop the sorted list and put it into a new insertion order Map LinkedHashMap
        val sortedMap: MutableMap<String, Double> = LinkedHashMap()
        for ((key, value) in list) {
            sortedMap[key] = value
        }
        return sortedMap
    }

    fun <T, E> getKeysByValue(map: Map<T, E>, value: E): Set<T> {
        val keys: MutableSet<T> = HashSet()
        for ((key, value1) in map) {
            if (value == value1) {
                keys.add(key)
            }
        }
        return keys
    }

    fun getMarginPixel(value: Float, displayMetrics: DisplayMetrics): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, displayMetrics).toInt()
}
