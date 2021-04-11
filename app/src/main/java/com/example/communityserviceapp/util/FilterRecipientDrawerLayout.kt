package com.example.communityserviceapp.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import androidx.lifecycle.LifecycleOwner
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.repository.JobNatureRepository
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_FILTER
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.*
import javax.inject.Inject

/**
 * This class is specifically used for code-reuse via composition (DI) by fragments that
 * wish to implement recipient filter drawer layout.
 *
 * The main purpose of this class are:
 * 1) Assisting in inflating ChipGroup (Rating, Location, JobNature)
 * 2) Generate Recipient Filter SQL Query
 * 3) Generate Recipient Filter Keywords
 * 4) Help to clear checked chips for specified chip groups
 *
 * Refer to usage example in "RecipientFilterFragment".
 */
class FilterRecipientDrawerLayout @Inject constructor(private val jobNatureRepository: JobNatureRepository) {

    private val sqlQuery = StringBuilder()
    private val statesMap: LinkedHashMap<String?, String?> = LinkedHashMap()

    fun fillRatingChipGroup(context: Context, ratingChipGroup: ChipGroup) {
        val ratingFilter = context.resources.getStringArray(R.array.rating_filter)
        for (rating in ratingFilter) {
            val chip = inflateChip(CHIP_TYPE_FILTER, rating, context)
            ratingChipGroup.addView(chip)
        }
    }

    fun fillReviewChipGroup(context: Context, reviewChipGroup: ChipGroup) {
        var chip = inflateChip(CHIP_TYPE_FILTER, "No Review", context)
        reviewChipGroup.addView(chip)
        chip = inflateChip(CHIP_TYPE_FILTER, "With Review", context)
        reviewChipGroup.addView(chip)
    }

    @SuppressLint("ResourceType")
    fun fillLocationChipGroup(context: Context, locationChipGroup: ChipGroup) {
        val statesArrayOfArrays = context.resources.obtainTypedArray(R.array.states_array_of_arrays)
        // Put all the states value to linkedHashMap by reading from string resource file
        var stateTypedArray: TypedArray? = null
        for (i in 0 until statesArrayOfArrays.length()) {
            val id = statesArrayOfArrays.getResourceId(i, -1)
            check(id != -1) { "R.array.states_array_of_arrays is not valid" }
            stateTypedArray = context.resources.obtainTypedArray(id)
            statesMap[stateTypedArray.getString(1)] = stateTypedArray.getString(0)
        }
        stateTypedArray?.recycle()
        statesArrayOfArrays.recycle()

        // Get all the key of linkedHashMap (Malaysia's state) and inflate chip
        val keys: Set<String?> = statesMap.keys
        val iterator = keys.iterator()
        while (iterator.hasNext()) {
            val chip = inflateChip(CHIP_TYPE_FILTER, iterator.next().toString(), context)
            locationChipGroup.addView(chip)
        }
    }

    fun fillJobNatureChipGroup(
        context: Context,
        lifecycleOwner: LifecycleOwner?,
        jobNatureChipGroup: ChipGroup
    ) {
        // Fetch recipient job nature from local db and inflate ChipGroup
        lifecycleOwner?.let {
            jobNatureRepository.getAllJobNature().observe(
                it,
                { jobNatureList ->
                    if (!jobNatureList.isNullOrEmpty()) {
                        jobNatureChipGroup.removeAllViews()
                        for (jobNature in jobNatureList) {
                            val chip = inflateChip(CHIP_TYPE_FILTER, jobNature.type, context)
                            jobNatureChipGroup.addView(chip)
                        }
                    }
                }
            )
        }
    }

    fun generateFilterSQLQuery(
        ratingChipGroup: ChipGroup,
        locationChipGroup: ChipGroup,
        jobNatureChipGroup: ChipGroup,
        reviewChipGroup: ChipGroup
    ): String? {
        val checkedRatingID = ratingChipGroup.checkedChipIds
        val checkedLocationIDs = locationChipGroup.checkedChipIds
        val checkedJobNatureIDs = jobNatureChipGroup.checkedChipIds
        val checkedReviewID = reviewChipGroup.checkedChipIds
        sqlQuery.setLength(0) // clear SQL query

        // Check if any filter (rating / location / job nature) is selected
        return if (checkedRatingID.isEmpty() && checkedLocationIDs.isEmpty() &&
            checkedJobNatureIDs.isEmpty() && checkedReviewID.isEmpty()
        ) {
            null
        } else {
            sqlQuery.append("SELECT * FROM recipient WHERE ")
            // check if any rating filter is selected
            if (checkedRatingID.isNotEmpty()) {
                val ratingChipText = ratingChipGroup.findViewById<Chip>(checkedRatingID[0]).text[0]
                sqlQuery.append("(rating >= $ratingChipText) ")

                appendSQLQueryBasedOnRatingFilterResult(
                    reviewChipGroup,
                    checkedReviewID,
                    jobNatureChipGroup,
                    locationChipGroup,
                    false,
                    checkedLocationIDs,
                    checkedJobNatureIDs
                )
            } else {
                appendSQLQueryBasedOnRatingFilterResult(
                    reviewChipGroup,
                    checkedReviewID,
                    jobNatureChipGroup,
                    locationChipGroup,
                    true,
                    checkedLocationIDs,
                    checkedJobNatureIDs
                )
            }
            sqlQuery.toString()
        }
    }

    private fun appendSQLQueryBasedOnRatingFilterResult(
        reviewChipGroup: ChipGroup,
        checkedReviewID: List<Int>,
        jobNatureChipGroup: ChipGroup,
        locationChipGroup: ChipGroup,
        isRatingFilterEmpty: Boolean,
        checkedLocationIDs: List<Int>,
        checkedJobNatureIDs: List<Int>
    ) {
        if (isRatingFilterEmpty) {
            // Rating Filter is empty
            appendLocationFilterToSQLQuery(
                reviewChipGroup,
                checkedReviewID,
                jobNatureChipGroup,
                locationChipGroup,
                checkedLocationIDs,
                checkedJobNatureIDs,
                true
            )
            // Check if location filter is empty
            if (checkedLocationIDs.isEmpty()) {
                // Location filter is empty
                appendJobNatureFilterToSQLQuery(
                    reviewChipGroup, checkedReviewID, jobNatureChipGroup,
                    checkedJobNatureIDs, true
                )
            }
        } else {
            // Rating Filter is NOT empty
            appendLocationFilterToSQLQuery(
                reviewChipGroup,
                checkedReviewID,
                jobNatureChipGroup,
                locationChipGroup,
                checkedLocationIDs,
                checkedJobNatureIDs,
                false
            )
            appendJobNatureFilterToSQLQuery(
                reviewChipGroup, checkedReviewID, jobNatureChipGroup,
                checkedJobNatureIDs, false
            )
        }
    }

    private fun appendLocationFilterToSQLQuery(
        reviewChipGroup: ChipGroup,
        checkedReviewID: List<Int>,
        jobNatureChipGroup: ChipGroup,
        locationChipGroup: ChipGroup,
        checkedLocationIDs: List<Int>,
        checkedJobNatureIDs: List<Int>,
        isRatingFilterEmpty: Boolean
    ) {
        // Check if location filter is empty
        if (checkedLocationIDs.isNotEmpty()) {
            // Location Filter is NOT empty
            // Check if rating filter is empty or not
            if (!isRatingFilterEmpty) {
                // Rating filter is NOT empty
                sqlQuery.append("AND ")
            }
            sqlQuery.append("(")
            var firstInsertion = true
            for (id in checkedLocationIDs) {
                val locationChip: Chip = locationChipGroup.findViewById(id)
                if (!firstInsertion) {
                    sqlQuery.append(" OR ")
                }
                sqlQuery.append("state LIKE \"%" + statesMap[locationChip.text] + "%\"")
                firstInsertion = false
            }
            sqlQuery.append(") ")
            // Check if rating filter is empty or not
            if (isRatingFilterEmpty) {
                // Rating filter is empty
                appendJobNatureFilterToSQLQuery(
                    reviewChipGroup, checkedReviewID, jobNatureChipGroup,
                    checkedJobNatureIDs, false
                )
            }
        }
    }

    private fun appendJobNatureFilterToSQLQuery(
        reviewChipGroup: ChipGroup,
        checkedReviewID: List<Int>,
        jobNatureChipGroup: ChipGroup,
        checkedJobNatureIDs: List<Int>,
        isBothRatingAndLocationFilterEmpty: Boolean
    ) {
        // Check if job nature filter is empty
        if (checkedJobNatureIDs.isNotEmpty()) {
            // job nature filter is NOT empty
            // check if both rating and location filter is empty
            if (!isBothRatingAndLocationFilterEmpty) {
                // either rating or location filter is not empty
                sqlQuery.append("AND ")
            }
            sqlQuery.append("(")
            var firstInsertion = true
            for (id in checkedJobNatureIDs) {
                val jobNatureChip: Chip = jobNatureChipGroup.findViewById(id)
                if (!firstInsertion) {
                    sqlQuery.append(" OR ")
                }
                sqlQuery.append("jobNature LIKE \"%" + jobNatureChip.text + "%\"")
                firstInsertion = false
            }
            sqlQuery.append(")")
            appendReviewFilterToSQLQuery(reviewChipGroup, checkedReviewID, false)
        } else {
            if (isBothRatingAndLocationFilterEmpty) {
                appendReviewFilterToSQLQuery(reviewChipGroup, checkedReviewID, true)
            } else {
                appendReviewFilterToSQLQuery(reviewChipGroup, checkedReviewID, false)
            }
        }
    }

    private fun appendReviewFilterToSQLQuery(
        reviewChipGroup: ChipGroup,
        checkedReviewID: List<Int>,
        isRatingAndLocationAndJobNatureFilterEmpty: Boolean
    ) {
        // Check if review filter is empty
        if (checkedReviewID.isNotEmpty()) {
            // review filter is NOT empty
            // check if rating, location and job nature filter is empty
            if (!isRatingAndLocationAndJobNatureFilterEmpty) {
                // either rating, location or job nature filter is not empty
                sqlQuery.append(" AND ")
            }
            val reviewChipText = reviewChipGroup.findViewById<Chip>(checkedReviewID[0]).text.toString()
            when (reviewChipText.first()) {
                // There is only two possible option: "No Review" or "With Review"
                // So check the first character is 'N' or 'W'
                'N' -> {
                    sqlQuery.append("(totalReviews = 0)")
                }
                'W' -> {
                    sqlQuery.append("(totalReviews > 0)")
                }
            }
        }
    }

    fun generateFilterKeywords(
        ratingChipGroup: ChipGroup,
        locationChipGroup: ChipGroup,
        jobNatureChipGroup: ChipGroup,
        reviewChipGroup: ChipGroup
    ): Array<String> {
        val filterKeywords = ArrayList<String>()
        val checkedRatingID = ratingChipGroup.checkedChipIds
        val checkedLocationIDs = locationChipGroup.checkedChipIds
        val checkedJobNatureIDs = jobNatureChipGroup.checkedChipIds
        val checkedReviewID = reviewChipGroup.checkedChipIds

        if (checkedRatingID.isNotEmpty()) {
            val ratingChip = ratingChipGroup.findViewById<Chip>(checkedRatingID[0])
            filterKeywords.add(ratingChip.text.toString())
        }
        if (checkedLocationIDs.isNotEmpty()) {
            for (id in checkedLocationIDs) {
                val locationChip: Chip = locationChipGroup.findViewById(id)
                filterKeywords.add(locationChip.text.toString())
            }
        }
        if (checkedJobNatureIDs.isNotEmpty()) {
            for (id in checkedJobNatureIDs) {
                val jobNatureChip: Chip = jobNatureChipGroup.findViewById(id)
                filterKeywords.add(jobNatureChip.text.toString())
            }
        }
        if (checkedReviewID.isNotEmpty()) {
            val reviewChip = reviewChipGroup.findViewById<Chip>(checkedReviewID[0])
            filterKeywords.add(reviewChip.text.toString())
        }
        return filterKeywords.toTypedArray()
    }

    fun clearCheckForAllChipGroup(vararg chipGroups: ChipGroup) {
        for (chipGroup in chipGroups) {
            chipGroup.clearCheck()
        }
    }
}
