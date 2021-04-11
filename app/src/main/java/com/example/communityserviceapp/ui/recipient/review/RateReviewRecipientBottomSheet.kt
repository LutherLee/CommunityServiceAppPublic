package com.example.communityserviceapp.ui.recipient.review

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.view.*
import android.widget.RatingBar
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RateReviewRecipientBottomSheetBinding
import com.example.communityserviceapp.ui.recipient.RecipientViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_FILTER
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_EMPTY_DEFAULT
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_VALID
import com.example.communityserviceapp.util.bottomSheet.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

@AndroidEntryPoint
class RateReviewRecipientBottomSheet : BaseBottomSheet() {

    private val recipientViewModel: RecipientViewModel by viewModels()
    private lateinit var binding: RateReviewRecipientBottomSheetBinding
    private val args: RateReviewRecipientBottomSheetArgs by navArgs()
    private lateinit var currentRecipient: Recipient
    private var shouldShowConfirmExitAlertDialog = true
    private var toast: Toast? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener { dialogInterface ->
                val bottomSheetDialog = dialogInterface as BottomSheetDialog
                expandBottomSheetToViewHeight(bottomSheetDialog)
            }
            setOnKeyListener { _: DialogInterface, keyCode: Int, keyEvent: KeyEvent ->
                if (keyCode == KeyEvent.KEYCODE_BACK &&
                    keyEvent.action == KeyEvent.ACTION_UP &&
                    shouldShowConfirmExitAlertDialog
                ) {
                    showConfirmExitDialog()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        // Return an empty error result to indicate finish so RecipientBottomSheet will enable UI interaction
        onBottomSheetDismissListener?.invoke(Result.Error())
        super.onDismiss(dialog)
    }

    override fun setCancelable(cancelable: Boolean) {
        makeBottomSheetUncancellable(requireDialog(), cancelable)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RateReviewRecipientBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            recipientViewmodel = recipientViewModel
        }
        return binding.root
    }

    override fun onDestroy() {
        toast?.cancel()
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        currentRecipient = args.recipient
        setupActionBar()
        setupListeners()
        subscribeUI()
    }

    private fun setupActionBar() {
        val sourceString = "<i>Rate Recipient</i><br><b>${currentRecipient.recipientName}</b>"
        binding.toolbarTextview.text = Html.fromHtml(sourceString)
        binding.recipientRating.text =
            String.format(Locale.ROOT, "%.1f", currentRecipient.recipientRating)
    }

    private fun setupListeners() {
        binding.exitButton.setOnClickListener {
            onBottomSheetDismissListener?.invoke(Result.Error())
            findNavController().navigateUp()
        }

        binding.cancelButton.setOnClickListener {
            onBottomSheetDismissListener?.invoke(Result.Error())
            findNavController().navigateUp()
        }

        binding.ratingBar.setOnRatingBarChangeListener { ratingBar: RatingBar, rating: Float, fromUser: Boolean ->
            binding.nextButton.isEnabled = rating != 0F
        }

        binding.nextButton.setOnClickListener {
            val userRating = binding.ratingBar.rating
            fillRecipientReviewTagChipGroup(userRating, binding.recipientReviewTagChipGroup)

            if (userRating == 0F) {
                showToast("Please select a rating")
            } else {
                binding.userRatedRatingBar.rating = userRating
                performCrossFadeAnimation(
                    binding.rateRecipientContent,
                    binding.reviewRecipientContent
                )
                val sourceString =
                    "<i>Review Recipient</i><br><b>${currentRecipient.recipientName}</b>"
                binding.toolbarTextview.text = Html.fromHtml(sourceString)
            }
        }

        binding.changeRatingButton.setOnClickListener {
            toast?.cancel()
            rateRecipient()
        }

        binding.backButton.setOnClickListener {
            toast?.cancel()
            rateRecipient()
        }

        binding.doneButton.setOnClickListener {
            hideKeyboard()
            toast?.cancel()

            val userRating = binding.ratingBar.rating
            val formValueStatus = recipientViewModel.isRecipientReviewValid()
            var isFormValid = false
            if (formValueStatus == VALUE_STATUS_VALID) {
                isFormValid = true
            }

            if (userRating in 1.0..5.0 && isFormValid) {
                checkConnectionBeforeUpdateRecipientReview(userRating, args.currentUserID)
            } else {
                showToast("Oops, an unknown error occurred. Please try again")
            }
        }
    }

    private fun subscribeUI() {
        recipientViewModel.recipientReview.observe(
            viewLifecycleOwner,
            {
                when (recipientViewModel.isRecipientReviewValid()) {
                    VALUE_STATUS_EMPTY_DEFAULT, VALUE_STATUS_INVALID -> {
                        binding.doneButton.isEnabled = false
                    }
                    VALUE_STATUS_VALID -> {
                        binding.doneButton.isEnabled = true
                    }
                }
            }
        )
    }

    private fun checkConnectionBeforeUpdateRecipientReview(
        userRating: Float,
        currentUserID: String
    ) {
        lifecycleScope.launch {
            val hasConnection = withContext(Dispatchers.IO) {
                checkConnectionStatusToFirebase()
            }
            if (hasConnection) {
                enableLoadingAnimation()
                val selectedRecipientTags = getSelectedRecipientTags()
                updateRecipientReview(userRating, selectedRecipientTags, currentUserID)
            } else {
                showToast(getString(R.string.default_network_error))
                recipientViewModel.enableLoadingProgressBar.value = false
                shouldShowConfirmExitAlertDialog = true
            }
        }
    }

    private fun updateRecipientReview(
        userRating: Float,
        selectedRecipientTags: String,
        currentUserID: String
    ) {
        lifecycleScope.launch {
            val result = recipientViewModel.updateRecipientReviewInFirebase(
                userRating,
                selectedRecipientTags,
                currentUserID,
                args.recipient.recipientID
            )
            when (result) {
                is Result.Success -> {
                    onBottomSheetDismissListener?.invoke(Result.Success(result.data))
                    recipientViewModel.updateRecommendationMetadata(args.recipient)
                    dismiss()
                }
                is Result.Error -> {
                    disableLoadingAnimation()
                    showToast("Failed to submit review, retry again later")
                }
            }
            enableDialogAndUIInteraction()
            shouldShowConfirmExitAlertDialog = true
        }
    }

    private fun performCrossFadeAnimation(viewToFadeOut: View, viewToFadeIn: View) {
        viewToFadeOut.animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    viewToFadeOut.visibility = View.GONE
                    viewToFadeIn.apply {
                        alpha = 0f
                        visibility = View.VISIBLE
                        animate().alpha(1f).setDuration(200).setListener(null)
                    }
                    Handler().postDelayed(
                        {
                            try {
                                val bottomSheet =
                                    requireDialog().findViewById<View>(R.id.design_bottom_sheet)
                                val behavior: BottomSheetBehavior<*> =
                                    BottomSheetBehavior.from(bottomSheet)
                                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                                requireView().parent.requestLayout()
                                behavior.peekHeight = requireView().height
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        200
                    )
                }
            })
    }

    private fun fillRecipientReviewTagChipGroup(userRating: Float, chipGroup: ChipGroup) {
        chipGroup.removeAllViews()

        val tags = when {
            userRating < 3.0 -> {
                requireContext().resources.getStringArray(R.array.recipient_review_tag_negative_review)
            }
            userRating >= 4.0 -> {
                requireContext().resources.getStringArray(R.array.recipient_review_tag_positive_review)
            }
            else -> {
                requireContext().resources.getStringArray(R.array.recipient_review_tag_neutral_review)
            }
        }
        for (tag in tags) {
            val chip = inflateChip(CHIP_TYPE_FILTER, tag, requireContext())
            chipGroup.addView(chip)
        }
    }

    private fun getSelectedRecipientTags(): String {
        val ids: List<Int> = binding.recipientReviewTagChipGroup.checkedChipIds
        val tags = StringBuilder()
        var firstInsertion = true
        for (id in ids) {
            val tagChip = binding.recipientReviewTagChipGroup.findViewById<Chip>(id)
            if (!firstInsertion) {
                tags.append(", ${tagChip.text}")
            } else {
                tags.append("${tagChip.text}")
                firstInsertion = false
            }
        }
        return tags.toString()
    }

    private fun enableLoadingAnimation() {
        disableDialogAndUIInteraction()
        shouldShowConfirmExitAlertDialog = false
        recipientViewModel.enableLoadingProgressBar.value = true

        binding.apply {
            contentContainer.visibility = View.GONE
            appBarLayout.visibility = View.GONE
            loadingContainer.visibility = View.VISIBLE
            loadingAnimationView.resumeAnimation()
        }
    }

    private fun disableLoadingAnimation() {
        recipientViewModel.enableLoadingProgressBar.value = false
        binding.apply {
            loadingAnimationView.visibility = View.GONE
            loadingAnimationView.pauseAnimation()
            contentContainer.visibility = View.VISIBLE
            appBarLayout.visibility = View.VISIBLE
        }
    }

    private fun showConfirmExitDialog() {
        toast?.cancel()
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDimAmountAlertDialogTheme)
            .setTitle("Stop Reviewing?")
            .setMessage(getString(R.string.confirm_exit_message))
            .setPositiveButton("Confirm") { dialog: DialogInterface, id: Int ->
                onBottomSheetDismissListener?.invoke(Result.Error())
                findNavController().navigateUp()
            }
            .setNegativeButton("Cancel") { dialog: DialogInterface, id: Int ->
                onBottomSheetDismissListener?.invoke(Result.Error())
                dismiss()
            }
            .show()
    }

    private fun rateRecipient() {
        performCrossFadeAnimation(binding.reviewRecipientContent, binding.rateRecipientContent)
        val sourceString = "<i>Rate Recipient</i><br><b>${currentRecipient.recipientName}</b>"
        binding.toolbarTextview.text = Html.fromHtml(sourceString)
    }

    private fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast?.show()
    }
}
