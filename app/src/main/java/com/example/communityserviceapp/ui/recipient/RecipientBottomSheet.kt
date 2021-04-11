package com.example.communityserviceapp.ui.recipient

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RecipientBottomSheetBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_ACTION
import com.example.communityserviceapp.util.bottomSheet.*
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.material.appbar.AppBarLayout.OnOffsetChangedListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FieldValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import kotlin.math.abs

@AndroidEntryPoint
class RecipientBottomSheet : BaseBottomSheet() {
    private val recipientViewModel: RecipientViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: RecipientBottomSheetBinding
    private lateinit var currentRecipient: Recipient
    private val args: RecipientBottomSheetArgs by navArgs()
    private var currentUserID: String? = null
    private var isFavourited = false
    private var recipientNumOfSocialMediaCounter = 0
    private var numOfBadUrlFeedbackSubmitted = 0
    private var hasUpdatedUserRecommendationMetadata = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenBottomSheetDialogThemeDefaultAnim)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            setOnShowListener {
                val bottomSheetDialog = it as BottomSheetDialog
                disableBottomSheetDragDownToDismiss(bottomSheetDialog)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshUser(this.javaClass.simpleName)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RecipientBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        recipientViewModel.setRecipient(args.recipient) // Pass recipient for viewModel to get recipient item
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadBannerAd()
        setupListener()
        subscribeUI()
        setupActionBar()
        setupSwipeRefreshColorScheme(binding.swipeRefreshLayout)
    }

    override fun onDestroy() {
        onBottomSheetDismissListener = null
        super.onDestroy()
    }

    private fun setupActionBar() {
        Glide.with(requireView())
            .load(args.recipient.recipientImageUrl)
            .error(R.drawable.ic_baseline_image_grey_24dp)
            .into(binding.roundedImageView)
        val sourceString = "<i>Recipient Details</i><br><b>${args.recipient.recipientName}</b>"
        binding.toolbarTextview.text = Html.fromHtml(sourceString, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun subscribeUI() {
        recipientViewModel.currentRecipient.observe(
            viewLifecycleOwner,
            { recipient ->
                recipient?.let {
                    currentRecipient = recipient
                    val jobNature = recipient.recipientJobNature.split(", ").toTypedArray()
                    loadRecipientUI(recipient, jobNature)
                    updateUserRecommendationMetadata(recipient.recipientID, jobNature)
                }
                binding.swipeRefreshLayout.isRefreshing = false
            }
        )

        mainViewModel.currentUserID.observe(
            viewLifecycleOwner,
            {
                currentUserID = it
                if (it != null) {
                    lifecycleScope.launch {
                        val isFavorited = withContext(Dispatchers.IO) {
                            recipientViewModel.checkRecipientIsFavorited(
                                it,
                                currentRecipient.recipientID
                            )
                        }
                        if (isFavorited) {
                            setFavoriteButtonAsChecked()
                        }
                    }
                }
            }
        )
    }

    private fun reviewRecipient() {
        val currentUser = currentFirebaseUser
        if (currentUser == null) {
            Snackbar.make(requireView(), "Login to review recipient", Snackbar.LENGTH_SHORT)
                .apply {
                    setAction("Login") {
                        navigateToOtherFragmentByDestinationID(R.id.loginFragment)
                    }
                }.show()
        } else {
            // need to disable interaction as when uploading review can still scroll this bottomsheet
            disableDialogInteraction(requireDialog())
            val action =
                RecipientBottomSheetDirections.actionRecipientBottomSheetToRateReviewRecipientBottomSheet(
                    currentUser.uid,
                    currentRecipient
                )
            findNavController().navigate(action)
        }
    }

    private fun shareRecipient() {
        val sharingIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                getShareRecipientContentMessage(currentRecipient).toString()
            )
        }
        startActivity(Intent.createChooser(sharingIntent, "Share via"))
    }

    private fun getShareRecipientContentMessage(recipient: Recipient): StringBuilder {
        val message = StringBuilder()
        message.append("${recipient.recipientName}\n\nAddress: ${recipient.recipientAddress}")
        val email = recipient.recipientEmail
        if (email.isNotEmpty()) {
            message.append("\nEmail: $email")
        }
        val website = recipient.recipientWebsite
        if (website.isNotEmpty()) {
            message.append("\nWebsite: $website")
        }
        val contact = recipient.recipientPhoneNum
        if (contact.isNotEmpty()) {
            message.append("\nContact: $contact")
        }
        val fax = recipient.recipientFaxNum
        if (fax.isNotEmpty()) {
            message.append("\nFax: $fax")
        }
        val yearEstablished = recipient.recipientYearEstablished
        if (yearEstablished != 0) message.append("\nYear Established: ${recipient.recipientYearEstablished}")
        val registrationNum = recipient.recipientRegistrationNum
        if (registrationNum.isNotEmpty()) {
            message.append("\nRegistration Number: $registrationNum")
        }

        if (recipientNumOfSocialMediaCounter > 0) {
            message.append("\n")
        }
        val instagram = recipient.instagram
        if (instagram.isNotEmpty()) {
            message.append("\nInstagram: $instagram")
        }
        val linkedin = recipient.linkedin
        if (linkedin.isNotEmpty()) {
            message.append("\nLinkedIn: $linkedin")
        }
        val facebook = recipient.facebook
        if (facebook.isNotEmpty()) {
            message.append("\nFacebook: $facebook")
        }
        val twitter = recipient.twitter
        if (twitter.isNotEmpty()) {
            message.append("\nTwitter: $twitter")
        }
        val vimeo = recipient.vimeo
        if (vimeo.isNotEmpty()) {
            message.append("\nVimeo: $vimeo")
        }
        val youtube = recipient.youtube
        if (youtube.isNotEmpty()) {
            message.append("\nYoutube: $youtube")
        }
        val pinterest = recipient.pinterest
        if (pinterest.isNotEmpty()) {
            message.append("\nPinterest: $pinterest")
        }
        val flickr = recipient.flickr
        if (flickr.isNotEmpty()) {
            message.append("\nFlickr: $flickr")
        }

        return message
    }

    private fun getDirectionsToRecipientLocation() {
        val latitude = currentRecipient.latitude
        val longitude = currentRecipient.longitude
        val gmmIntentUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)

        if (mapIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            showShortToast("No application found to be able to open up a map")
        }
    }

    private fun loadBannerAd() {
        Timber.d("Loading Banner Ad")
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
        binding.adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                binding.adView.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Timber.d("Ad Load Failed")
            }

            override fun onAdClosed() {
                binding.adView.visibility = View.GONE
            }
        }
    }

    private fun setupListener() {
        onBottomSheetDismissListener = { result ->
            if (result is Result.Success) {
                val resultData = result.data as MutableMap<String, Any>
                val shouldNavigateToReviewRecipientBottomSheet =
                    resultData["navigateToReviewRecipientBottomSheet"] as Boolean

                if (shouldNavigateToReviewRecipientBottomSheet) {
                    // This is invoked from RecipientReviewBottomSheet 
                    // Need to delay as nav controller may not update fast enough causing 
                    // illegalArgumentException
                    Handler().postDelayed(
                        {
                            checkIfMayNavigate { reviewRecipient() }
                        },
                        100
                    )
                } else {
                    val shouldUpdateRecipientUI = resultData["shouldUpdateRecipientUI"]
                    if (shouldUpdateRecipientUI != null) {
                        // This is invoked from RecipientReviewBottomSheet
                        recipientViewModel.refreshRecipientDetails(currentRecipient.recipientID)
                    } else {
                        // This is invoked from RateReviewRecipientBottomSheet
                        recipientViewModel.refreshRecipientDetails(currentRecipient.recipientID)
                        updateRecipientRatingAndReview(resultData)
                        showShortSnackbar("Review Submitted")
                    }
                }
            }
            enableDialogInteraction(requireDialog())
        }

        binding.appBarLayout.addOnOffsetChangedListener(
            OnOffsetChangedListener { appBarLayout, verticalOffset ->
                if (abs(verticalOffset) - appBarLayout.totalScrollRange == 0) {
                    // Collapsed
                    binding.toolbar.setBackgroundColor(getColor(android.R.color.white))
                    binding.recipientFeedbackButton.backgroundTintList =
                        ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                    binding.backButton.backgroundTintList =
                        ContextCompat.getColorStateList(requireContext(), android.R.color.white)
                    binding.backButton.setIconTintResource(R.color.black)
                    binding.recipientImageCardview.visibility = View.VISIBLE
                    binding.toolbarTextview.visibility = View.VISIBLE
                } else {
                    // Expanded
                    binding.toolbar.setBackgroundResource(R.drawable.background_toolbar_transparent)
                    binding.recipientImageCardview.visibility = View.GONE
                    binding.toolbarTextview.visibility = View.GONE
                    binding.backButton.setIconTintResource(R.color.eighty_percent_of_black)
                    binding.recipientFeedbackButton.backgroundTintList =
                        ContextCompat.getColorStateList(
                            requireContext(),
                            R.color.sixty_percent_of_background_grey
                        )
                    binding.backButton.backgroundTintList =
                        ContextCompat.getColorStateList(
                            requireContext(),
                            R.color.sixty_percent_of_background_grey
                        )
                }
            }
        )

        binding.swipeRefreshLayout.setOnRefreshListener {
            mainViewModel.refreshUser(this.javaClass.simpleName)
            recipientViewModel.refreshRecipientDetails(currentRecipient.recipientID)
        }

        binding.reviewRecipientButton.setOnClickListener {
            checkIfMayNavigate { reviewRecipient() }
        }

        binding.getDirectionButton.setOnClickListener { getDirectionsToRecipientLocation() }

        binding.shareRecipientButton.setOnClickListener { shareRecipient() }

        binding.recipientFeedbackButton.setOnClickListener {
            val currentUserEmail = currentFirebaseUser?.email
            val action =
                RecipientBottomSheetDirections.actionRecipientBottomSheetToFeedbackFragment(
                    currentUserEmail,
                    currentRecipient.recipientName
                )
            findNavController().navigate(action)
        }

        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        binding.favoriteButton.setOnClickListener {
            lifecycleScope.launch {
                if (currentUserID != null) {
                    binding.favoriteButton.isEnabled = false
                    val hasConnection = withContext(Dispatchers.IO) {
                        checkConnectionStatusToFirebase()
                    }
                    if (isFavourited) {
                        if (hasConnection) {
                            unfavoriteRecipient()
                        } else {
                            showShortSnackbar(getString(R.string.default_network_error))
                            binding.favoriteButton.isEnabled = true
                        }
                    } else {
                        if (hasConnection) {
                            favoriteRecipient()
                        } else {
                            showShortSnackbar(getString(R.string.default_network_error))
                            binding.favoriteButton.isEnabled = true
                        }
                    }
                } else {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.require_login_save_recipient_as_favorite_error),
                        Snackbar.LENGTH_SHORT
                    ).apply {
                        setAction("Login") {
                            navigateToOtherFragmentByDestinationID(R.id.loginFragment)
                        }
                    }.show()
                }
            }
        }

        binding.allReviewsButton.setOnClickListener {
            checkIfMayNavigate { navigateToRecipientReviewBottomSheet() }
        }

        binding.ratingOverallContainer.setOnClickListener {
            checkIfMayNavigate { navigateToRecipientReviewBottomSheet() }
        }
    }

    private fun unfavoriteRecipient() {
        lifecycleScope.launch {
            val isSuccessful = withContext(Dispatchers.IO) {
                recipientViewModel.saveRecipientAsFavoriteOrUnfavorite(true)
            }
            if (isSuccessful) {
                setFavoriteButtonAsUnchecked()
                Snackbar.make(
                    requireView(),
                    getString(R.string.recipient_removed_from_favorite),
                    Snackbar.LENGTH_SHORT
                ).apply {
                    setAction("Show All") {
                        navigateToOtherFragmentByDestinationID(R.id.recipientFavoriteFragment)
                    }
                }.show()
            } else {
                // When setValue() return a failure error (internal error)
                showShortSnackbar(getString(R.string.unknown_error))
            }
            binding.favoriteButton.isEnabled = true
        }
    }

    private fun favoriteRecipient() {
        lifecycleScope.launch {
            val isSuccessful = withContext(Dispatchers.IO) {
                recipientViewModel.saveRecipientAsFavoriteOrUnfavorite(false)
            }
            if (isSuccessful) {
                setFavoriteButtonAsChecked()
                Snackbar.make(
                    requireView(),
                    getString(R.string.recipient_added_to_favorite),
                    Snackbar.LENGTH_SHORT
                ).apply {
                    setAction("Show All") {
                        navigateToOtherFragmentByDestinationID(R.id.recipientFavoriteFragment)
                    }
                }.show()
            } else {
                // When setValue() return a failure error (internal error)
                showShortSnackbar(getString(R.string.unknown_error))
            }
            binding.favoriteButton.isEnabled = true
        }
    }

    private fun checkPictureSourceAndLoadIt(imageUrl: String) {
        Glide.with(requireContext())
            .load(imageUrl)
            .fitCenter()
            .placeholder(getCircularProgressDrawable(requireContext()))
            .error(R.drawable.ic_baseline_image_grey_24dp)
            .into(binding.recipientImage)
    }

    private fun setFavoriteButtonAsChecked() {
        binding.favoriteButton.icon = AppCompatResources.getDrawable(
            requireContext(),
            R.drawable.ic_baseline_favorite_red_24dp
        )
        binding.favoriteButton.setIconTintResource(R.color.colorSecondary)
        binding.favoriteButtonText.text = "Favourited"
        isFavourited = true
    }

    private fun setFavoriteButtonAsUnchecked() {
        binding.favoriteButton.icon = AppCompatResources.getDrawable(
            requireContext(),
            R.drawable.ic_baseline_favorite_outline_dimgray_24dp
        )
        binding.favoriteButton.setIconTintResource(R.color.bright_royal_blue)
        binding.favoriteButtonText.text = "Favourite"
        isFavourited = false
    }

    private fun updateRecipientRatingAndReview(userReviewMetadata: MutableMap<String, Any>) {
        val newAverageRating =
            String.format(Locale.ROOT, "%.1f", userReviewMetadata["newAverageRating"] as Double)
        val userRating = userReviewMetadata["userRating"]

        binding.ratingReviewRating.text = newAverageRating
        binding.ratingReviewRatingBar.rating = newAverageRating.toFloat()
        val newTotalReview = (binding.ratingReviewTotalReview.text).toString().toInt() + 1
        binding.ratingReviewTotalReview.text = newTotalReview.toString()

        when (userRating) {
            5 -> {
                binding.ratingReviewRating5ProgressBar.apply {
                    max += 1
                    progress += 1
                }
                binding.ratingReviewRating4ProgressBar.max += 1
                binding.ratingReviewRating3ProgressBar.max += 1
                binding.ratingReviewRating2ProgressBar.max += 1
                binding.ratingReviewRating1ProgressBar.max += 1
            }
            4 -> {
                binding.ratingReviewRating5ProgressBar.max += 1
                binding.ratingReviewRating4ProgressBar.apply {
                    max += 1
                    progress += 1
                }
                binding.ratingReviewRating3ProgressBar.max += 1
                binding.ratingReviewRating2ProgressBar.max += 1
                binding.ratingReviewRating1ProgressBar.max += 1
            }
            3 -> {
                binding.ratingReviewRating5ProgressBar.max += 1
                binding.ratingReviewRating4ProgressBar.max += 1
                binding.ratingReviewRating3ProgressBar.apply {
                    max += 1
                    progress += 1
                }
                binding.ratingReviewRating2ProgressBar.max += 1
                binding.ratingReviewRating1ProgressBar.max += 1
            }
            2 -> {
                binding.ratingReviewRating5ProgressBar.max += 1
                binding.ratingReviewRating4ProgressBar.max += 1
                binding.ratingReviewRating3ProgressBar.max += 1
                binding.ratingReviewRating2ProgressBar.apply {
                    max += 1
                    progress += 1
                }
                binding.ratingReviewRating1ProgressBar.max += 1
            }
            1 -> {
                binding.ratingReviewRating5ProgressBar.max += 1
                binding.ratingReviewRating4ProgressBar.max += 1
                binding.ratingReviewRating3ProgressBar.max += 1
                binding.ratingReviewRating2ProgressBar.max += 1
                binding.ratingReviewRating1ProgressBar.apply {
                    max += 1
                    progress += 1
                }
            }
        }
    }

    private fun loadRecipientUI(recipient: Recipient, jobNature: Array<String>) {
        checkPictureSourceAndLoadIt(recipient.recipientImageUrl)
        val yearEstablished = recipient.recipientYearEstablished
        if (yearEstablished != 0) {
            binding.recipientYearEstablished.text = yearEstablished.toString()
            binding.yearEstablishedText.visibility = View.VISIBLE
            binding.recipientYearEstablished.visibility = View.VISIBLE
        } else {
            binding.yearEstablishedText.visibility = View.GONE
            binding.recipientYearEstablished.visibility = View.GONE
        }

        binding.jobNatureChipGroup.removeAllViews() // remove all view first
        for (tag in jobNature) {
            val chip = inflateChip(CHIP_TYPE_ACTION, tag, requireContext())
            binding.jobNatureChipGroup.addView(chip)
        }
        // Setup info details
        setupRecipientInfoIfNotEmpty(
            binding.recipientRegistrationNum,
            binding.registrationNumLine,
            recipient.recipientRegistrationNum
        )
        setupRecipientInfoIfNotEmpty(
            binding.recipientContact,
            binding.contactLine,
            recipient.recipientPhoneNum
        )
        setupRecipientInfoIfNotEmpty(
            binding.recipientFax,
            binding.faxLine,
            recipient.recipientFaxNum
        )
        setupRecipientInfoIfNotEmpty(
            binding.recipientEmail,
            binding.emailLine,
            recipient.recipientEmail
        )
        setupRecipientInfoIfNotEmpty(
            binding.recipientWebsite,
            binding.websiteLine,
            recipient.recipientWebsite
        )
        // Setup Social Media Links
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientInstagram, recipient.instagram)
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientLinkedin, recipient.linkedin)
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientFacebook, recipient.facebook)
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientTwitter, recipient.twitter)
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientVimeo, recipient.vimeo)
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientYoutube, recipient.youtube)
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientPinterest, recipient.pinterest)
        setupRecipientSocialMediaLinksIfNotEmpty(binding.recipientFlickr, recipient.flickr)

        if (recipientNumOfSocialMediaCounter > 0) {
            binding.recipientSocialMedias.visibility = View.VISIBLE
        } else {
            binding.recipientSocialMedias.visibility = View.GONE
        }

        binding.recipientAddress.text = recipient.recipientAddress
        binding.recipientName.text = recipient.recipientName
        // Replace newline with <br />
        val recipientDetails = recipient.recipientDetail.replace("\n", "<br />")
        binding.recipientDetail.text =
            Html.fromHtml(recipientDetails, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.recipientDetail.movementMethod = LinkMovementMethod.getInstance()
        val rating = String.format(Locale.ROOT, "%.1f", recipient.recipientRating)
        binding.ratingReviewRating.text = rating
        binding.ratingReviewRatingBar.rating = rating.toFloat()
        val totalReviews = recipient.totalReviews
        binding.ratingReviewTotalReview.text = totalReviews.toString()

        binding.ratingReviewRating5ProgressBar.apply {
            max = totalReviews
            progress = recipient.numOf5Star
        }

        binding.ratingReviewRating4ProgressBar.apply {
            max = totalReviews
            progress = recipient.numOf4Star
        }

        binding.ratingReviewRating3ProgressBar.apply {
            max = totalReviews
            progress = recipient.numOf3Star
        }

        binding.ratingReviewRating2ProgressBar.apply {
            max = totalReviews
            progress = recipient.numOf2Star
        }

        binding.ratingReviewRating1ProgressBar.apply {
            max = totalReviews
            progress = recipient.numOf1Star
        }
    }

    private fun setupRecipientInfoIfNotEmpty(
        textViewToSetText: TextView,
        viewToHide: View,
        textToInsert: String
    ) {
        if (textToInsert.isNotEmpty()) {
            textViewToSetText.text = textToInsert
            viewToHide.visibility = View.VISIBLE
        } else {
            viewToHide.visibility = View.GONE
        }
    }

    private fun setupRecipientSocialMediaLinksIfNotEmpty(view: View, link: String) {
        if (link.isNotEmpty()) {
            view.setOnClickListener {
                try {
                    val urlLink = Uri.parse(link)
                    startActivity(Intent(Intent.ACTION_VIEW).setData(urlLink))
                } catch (e: Exception) {
                    Snackbar.make(
                        requireView(),
                        "Unable to open social media link",
                        Snackbar.LENGTH_SHORT
                    ).apply {
                        setAction("Report") { reportUrlLinkError(link) }.show()
                    }
                }
            }
            view.visibility = View.VISIBLE
            recipientNumOfSocialMediaCounter += 1
        } else {
            view.visibility = View.GONE
        }
    }

    private fun navigateToRecipientReviewBottomSheet() {
        val action =
            RecipientBottomSheetDirections.actionRecipientBottomSheetToRecipientReviewBottomSheet(
                currentRecipient
            )
        findNavController().navigate(action)
    }

    private fun navigateToOtherFragmentByDestinationID(destinationID: Int) {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        val resID = when (destinationID) {
            R.id.loginFragment -> {
                R.id.action_recipientBottomSheet_to_loginFragment
            }
            R.id.recipientFavoriteFragment -> {
                R.id.action_recipientBottomSheet_to_recipientFavoriteFragment
            }
            R.id.feedbackFragment -> {
                R.id.action_recipientBottomSheet_to_feedbackFragment
            }
            else -> null
        }
        // Perform delay navigation by 200ms
        resID?.let {
            Handler().postDelayed(
                {
                    requireDialog().dismiss()
                    findNavController().navigate(it)
                },
                200
            )
        }
    }

    private fun reportUrlLinkError(link: String) {
        if (numOfBadUrlFeedbackSubmitted >= recipientNumOfSocialMediaCounter) {
            showShortToast("You have already reported all the bad social media links")
        } else {
            val data = mutableMapOf<String, Any?>()
            data["title"] = "Social Media Link Error"
            data["message"] =
                "${currentRecipient.recipientName}\n${currentRecipient.recipientID}\n\nLink: $link"
            data["timestamp:"] = FieldValue.serverTimestamp()
            lifecycleScope.launch {
                val hasConnection = withContext(Dispatchers.IO) {
                    checkConnectionStatusToFirebase()
                }
                if (hasConnection) {
                    val isSuccessful = withContext(Dispatchers.IO) {
                        addFeedbackMessageToFirestore(data)
                    }
                    if (isSuccessful) {
                        numOfBadUrlFeedbackSubmitted += 1
                        showShortToast("Report Submitted")
                    } else {
                        showShortToast("Unable to send report, retry again later")
                    }
                } else {
                    showShortToast(getString(R.string.default_network_error))
                }
            }
        }
    }

    private fun updateUserRecommendationMetadata(recipientID: String, jobNature: Array<String>) {
        if (!hasUpdatedUserRecommendationMetadata) {
            val currentUserID = currentFirebaseUser?.uid
            recipientViewModel.updateUserHasViewedRecipient(
                currentUserID,
                recipientID
            )

            recipientViewModel.insertORUpdateRecommendationMetadata(
                currentUserID,
                jobNature,
                1
            )
            hasUpdatedUserRecommendationMetadata = true
        }
    }
}
