package com.example.communityserviceapp.ui.setRecommendationCriteria

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.SetRecommendationCriteriaFragmentBinding
import com.example.communityserviceapp.util.*
import com.google.android.material.chip.Chip
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SetRecommendationCriteriaFragment : Fragment() {

    private val setRecommendationCriteriaViewModel: SetRecommendationCriteriaViewModel by viewModels()
    private lateinit var binding: SetRecommendationCriteriaFragmentBinding
    private var errorToast: Toast? = null
    private var isKualaLumpurOptionChecked = false
    private var isSelangorOptionChecked = false
    private var isPutrajayaOptionChecked = false
    private var isOnStepTwo = false
    private var userSelectedLocation = StringBuilder()
    private var userSelectedJobNature = StringBuilder()
    @Inject lateinit var editor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isOnStepTwo) {
                        isOnStepTwo = false
                        animateViewFadeInAndOut(binding.setPreferredLocationContainer, binding.setPreferredJobNatureContainer)
                    } else {
                        findNavController().navigateUp()
                    }
                }
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding =
            SetRecommendationCriteriaFragmentBinding.inflate(inflater, container, false).apply {
                lifecycleOwner = viewLifecycleOwner
            }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        subscribeUI()
    }

    override fun onDestroyView() {
        errorToast = null
        super.onDestroyView()
    }

    private fun setupListeners() {
        binding.apply {
            skipSetLocationButton.setOnClickListener { findNavController().navigateUp() }

            imageButtonKualaLumpur.setOnClickListener {
                if (isKualaLumpurOptionChecked) showKualaLumpurOptionAsUnchecked() else showKualaLumpurOptionAsChecked()
            }

            imageButtonSelangor.setOnClickListener {
                if (isSelangorOptionChecked) showSelangorOptionAsUnchecked() else showSelangorOptionAsChecked()
            }

            imageButtonPutrajaya.setOnClickListener {
                if (isPutrajayaOptionChecked) showPutrajayaOptionAsUnchecked() else showPutrajayaOptionAsChecked()
            }

            selectAllLocationButton.setOnClickListener {
                showKualaLumpurOptionAsChecked()
                showSelangorOptionAsChecked()
                showPutrajayaOptionAsChecked()
            }

            resetSetLocationButton.setOnClickListener {
                showKualaLumpurOptionAsUnchecked()
                showSelangorOptionAsUnchecked()
                showPutrajayaOptionAsUnchecked()
            }

            nextButton.setOnClickListener {
                val numberOfSelectedOption = checkWhichOptionIsSelected()
                if (numberOfSelectedOption == 0) {
                    showNoLocationCriteriaSelectedError()
                } else {
                    cancelDisplayingToast()
                    isOnStepTwo = true
                    animateViewFadeInAndOut(binding.setPreferredJobNatureContainer, binding.setPreferredLocationContainer)
                }
            }

            selectAllJobNatureButton.setOnClickListener {
                val chipGroupSize = binding.jobNatureChipGroup.childCount
                for (i in 0 until chipGroupSize) {
                    val chip = binding.jobNatureChipGroup.getChildAt(i) as Chip
                    chip.isChecked = true
                }
            }

            skipSetJobNatureButton.setOnClickListener { findNavController().navigateUp() }

            doneButton.setOnClickListener {
                val checkedJobNatureIDs = binding.jobNatureChipGroup.checkedChipIds
                when {
                    checkedJobNatureIDs.isEmpty() -> {
                        showToastMessage(getString(R.string.no_job_nature_recommendation_criteria_selected))
                    }
                    userSelectedLocation.isEmpty() -> {
                        showToastMessage(getString(R.string.error_missing_location_recommendation_criteria))
                    }
                    else -> {
                        cancelDisplayingToast()
                        obtainAllSelectedJobNatureChipText(checkedJobNatureIDs)
                        storeUserSelectedRecommendationCriteriaInSharedPreferences(
                            currentFirebaseUser
                        )
                        showShortToast(requireContext(), "Recommendation criteria saved")
                        findNavController().navigateUp()
                    }
                }
            }

            resetSetJobNatureButton.setOnClickListener { binding.jobNatureChipGroup.clearCheck() }
        }
    }

    private fun subscribeUI() {
        setRecommendationCriteriaViewModel.allRecipientJobNatures.observe(
            viewLifecycleOwner,
            { jobNatureList ->
                if (!jobNatureList.isNullOrEmpty()) {
                    binding.jobNatureChipGroup.removeAllViews()
                    for (jobNature in jobNatureList) {
                        val chip = inflateChip(
                            Constants.CHIP_TYPE_FILTER,
                            jobNature.type,
                            requireContext()
                        )
                        binding.jobNatureChipGroup.addView(chip)
                    }
                }
            }
        )
    }

    private fun animateViewFadeInAndOut(viewToFadeIn: View, viewToFadeOut: View) {
        fadeOutAnimation(viewToFadeOut)
        fadeInAnimation(viewToFadeIn)
    }

    private fun fadeOutAnimation(viewToFadeOut: View) {
        ObjectAnimator.ofFloat(viewToFadeOut, "alpha", 1f, 0f).apply {
            duration = 300
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // We wanna set the view to GONE, after it's fade out. so it actually
                    // disappear from the layout & don't take up space.
                    viewToFadeOut.visibility = View.GONE
                }
            })
        }.start()
    }

    private fun fadeInAnimation(viewToFadeIn: View) {
        ObjectAnimator.ofFloat(viewToFadeIn, "alpha", 0f, 1f).apply {
            duration = 870
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    // We wanna set the view to VISIBLE, but with alpha 0. So it
                    // appear invisible in the layout.
                    viewToFadeIn.visibility = View.VISIBLE
                    viewToFadeIn.alpha = 0f
                }
            })
        }.start()
    }

    private fun showKualaLumpurOptionAsChecked() {
        binding.imageButtonKualaLumpur.alpha = 0.6f
        binding.kualaLumpurOptionCheckmark.visibility = View.VISIBLE
        isKualaLumpurOptionChecked = true
    }

    private fun showKualaLumpurOptionAsUnchecked() {
        binding.kualaLumpurOptionCheckmark.visibility = View.GONE
        binding.imageButtonKualaLumpur.alpha = 1f
        isKualaLumpurOptionChecked = false
    }

    private fun showSelangorOptionAsChecked() {
        binding.imageButtonSelangor.alpha = 0.6f
        binding.selangorOptionCheckmark.visibility = View.VISIBLE
        isSelangorOptionChecked = true
    }

    private fun showSelangorOptionAsUnchecked() {
        binding.selangorOptionCheckmark.visibility = View.GONE
        binding.imageButtonSelangor.alpha = 1f
        isSelangorOptionChecked = false
    }

    private fun showPutrajayaOptionAsChecked() {
        binding.imageButtonPutrajaya.alpha = 0.6f
        binding.putrajayaOptionCheckmark.visibility = View.VISIBLE
        isPutrajayaOptionChecked = true
    }

    private fun showPutrajayaOptionAsUnchecked() {
        binding.putrajayaOptionCheckmark.visibility = View.GONE
        binding.imageButtonPutrajaya.alpha = 1f
        isPutrajayaOptionChecked = false
    }

    private fun checkWhichOptionIsSelected(): Int {
        userSelectedLocation.clear()

        if (isKualaLumpurOptionChecked) {
            userSelectedLocation.append("KUL")
            if (isPutrajayaOptionChecked) {
                userSelectedLocation.append(", PJY")
            }
            if (isSelangorOptionChecked) {
                userSelectedLocation.append(", SGR")
            }
        } else {
            if (isPutrajayaOptionChecked) {
                userSelectedLocation.append("PJY")
                if (isSelangorOptionChecked) {
                    userSelectedLocation.append(", SGR")
                }
            } else {
                if (isSelangorOptionChecked) {
                    userSelectedLocation.append("SGR")
                }
            }
        }
        return userSelectedLocation.length
    }

    private fun showNoLocationCriteriaSelectedError() {
        cancelDisplayingToast()
        errorToast = Toast.makeText(
            requireContext(),
            getString(R.string.no_location_recommendation_criteria_selected),
            Toast.LENGTH_SHORT
        )
        errorToast?.show()
    }

    private fun obtainAllSelectedJobNatureChipText(checkedJobNatureIDs: List<Int>) {
        if (checkedJobNatureIDs.size > 1) {
            // more than one chip is selected
            var isFirstInsertion = true
            for (id in checkedJobNatureIDs) {
                if (!isFirstInsertion) {
                    userSelectedJobNature.append(", ")
                }
                val jobNatureChip: Chip = binding.jobNatureChipGroup.findViewById(id)
                userSelectedJobNature.append(jobNatureChip.text)
                isFirstInsertion = false
            }
        } else {
            // only one chip is selected
            val jobNatureChip: Chip =
                binding.jobNatureChipGroup.findViewById(checkedJobNatureIDs[0])
            userSelectedJobNature.append(jobNatureChip.text)
        }
    }

    private fun storeUserSelectedRecommendationCriteriaInSharedPreferences(currentUser: FirebaseUser?) {
        if (currentUser == null) {
            editor.putString(
                getString(R.string.user_selected_job_nature_recommendation_criteria),
                userSelectedJobNature.toString()
            ).commit()
            editor.putString(
                getString(R.string.user_selected_location_recommendation_criteria),
                userSelectedLocation.toString()
            ).commit()

            // Declare already set recommendation criteria
            editor.putBoolean(getString(R.string.has_set_recommendation_criteria), true).commit()
        } else {
            val currentUserID = currentUser.uid
            editor.putString(
                currentUserID + getString(R.string.user_selected_job_nature_recommendation_criteria),
                userSelectedJobNature.toString()
            ).commit()
            editor.putString(
                currentUserID + getString(R.string.user_selected_location_recommendation_criteria),
                userSelectedLocation.toString()
            ).commit()

            // Declare already set recommendation criteria
            editor.putBoolean(
                currentUserID + getString(R.string.has_set_recommendation_criteria),
                true
            ).commit()
            storeUserSelectedRecommendationCriteriaInFirebase(currentUserID)
        }
    }

    private fun storeUserSelectedRecommendationCriteriaInFirebase(currentUserID: String) {
        val data = mutableMapOf<String, Any?>()
        data["locationCriteria"] = userSelectedLocation.toString()
        data["jobNatureCriteria"] = userSelectedJobNature.toString()
        data["updatedAt"] = Timestamp.now().seconds
        storeUserRecommendationCriteria(data, currentUserID)
    }

    private fun showToastMessage(message: String) {
        cancelDisplayingToast()
        errorToast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        errorToast?.show()
    }

    private fun cancelDisplayingToast() = errorToast?.cancel()
}
