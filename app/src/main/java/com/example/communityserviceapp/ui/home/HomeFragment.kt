package com.example.communityserviceapp.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.PagerSnapHelper
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.HomeFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.ui.adapters.BannerPagerAdapter
import com.example.communityserviceapp.ui.adapters.CrowdFundingPagingAdapter
import com.example.communityserviceapp.ui.adapters.RecommendationAdapter
import com.example.communityserviceapp.ui.adapters.RecommendationAdapter.Companion.RECIPIENT_DIRECTION
import com.example.communityserviceapp.ui.adapters.RecommendationAdapter.Companion.RECIPIENT_ROOT_VIEW
import com.example.communityserviceapp.ui.adapters.RecommendationAdapter.Companion.RECIPIENT_SHARE
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.bottomSheet.onBottomSheetDismissListener
import com.example.communityserviceapp.util.bottomSheet.onShowAllJobNatureCategoryBottomSheetDismissListener
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FieldValue
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.createBalloon
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.ref.WeakReference

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val suggestionsViewModel: SuggestionsViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var recommendationAdapter: RecommendationAdapter? = null
    private var crowdFundingPagingAdapter: CrowdFundingPagingAdapter? = null
    private lateinit var bannerPagerAdapter: BannerPagerAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator
    private lateinit var binding: HomeFragmentBinding

    override fun onResume() {
        mainViewModel.refreshUser(this.javaClass.simpleName)
        super.onResume()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HomeFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onDestroy() {
        recommendationAdapter?.onRecipientItemClickListener = null
        crowdFundingPagingAdapter?.onCrowdFundingItemClickListener = null
        onBottomSheetDismissListener = null
        onShowAllJobNatureCategoryBottomSheetDismissListener = null
        (requireActivity() as AppCompatActivity).setSupportActionBar(null)
        super.onDestroy()
    }

    override fun onDestroyView() {
        binding.bannerViewpager.adapter = null
        recommendationAdapter = null
        crowdFundingPagingAdapter = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recommendationAdapter = RecommendationAdapter()
        crowdFundingPagingAdapter = CrowdFundingPagingAdapter()
        suggestionsViewModel.refreshUserSuggestions()
        setupRecyclerView()
        setupViewPager()
        setupListeners()
        subscribeUI()
    }

    private fun setupViewPager() {
        bannerPagerAdapter = BannerPagerAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        binding.bannerViewpager.adapter = bannerPagerAdapter
        tabLayoutMediator = TabLayoutMediator(
            binding.bannerTabLayout,
            binding.bannerViewpager
        ) { tab: TabLayout.Tab?, position: Int -> }
        tabLayoutMediator.attach()
        autoScrollViewPager()
    }

    private fun autoScrollViewPager() {
        AutoScrollViewPager(
            WeakReference(binding.bannerViewpager),
            WeakReference(bannerPagerAdapter),
            WeakReference(this)
        ).run()
    }

    private fun setupListeners() {
        crowdFundingPagingAdapter?.onCrowdFundingItemClickListener = { result ->
            when (result) {
                is Result.Error -> {
                    Snackbar.make(
                        requireView(),
                        "Unable to open social media link",
                        Snackbar.LENGTH_SHORT
                    ).apply {
                        setAction("Report") { reportUrlLinkError(result.error) }.show()
                    }
                }
                is Result.Success -> {
                    startActivity(Intent(Intent.ACTION_VIEW).setData(result.data))
                }
            }
        }

        recommendationAdapter?.onRecipientItemClickListener = { clickType, recipient ->
            when (clickType) {
                RECIPIENT_ROOT_VIEW -> {
                    navigateToRecipientBottomSheet(recipient)
                }
                RECIPIENT_SHARE -> {
                    shareRecipient(recipient)
                }
                RECIPIENT_DIRECTION -> {
                    getDirectionsToRecipientLocation(recipient)
                }
            }
        }

        onShowAllJobNatureCategoryBottomSheetDismissListener = { result ->
            if (result is Result.Success) {
                val resultData = result.data as MutableMap<*, *>
                val searchQuery = resultData["searchQuery"] as String
                val searchKeyword = resultData["searchKeyword"] as String
                val action =
                    HomeFragmentDirections.actionHomeFragmentToRecipientFilterFragment(
                        searchQuery, searchKeyword, null
                    )
                Handler().postDelayed(
                    {
                        findNavController().navigate(action)
                    },
                    200
                )
            }
        }

        binding.apply {
            favouriteButton.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_recipientFavoriteFragment)
            }

            toolbar.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_recipientSearchFragment)
            }

            recommendationGenerationInfo.setOnClickListener { showRecommendationGenerationInfo() }

            leftNav.setOnClickListener {
                var tab = bannerViewpager.currentItem
                if (tab > 0) {
                    tab--
                    bannerViewpager.currentItem = tab
                } else if (tab == 0) {
                    bannerViewpager.currentItem = bannerPagerAdapter.itemCount
                }
            }

            rightNav.setOnClickListener {
                var tab = bannerViewpager.currentItem
                tab++
                if (tab == bannerPagerAdapter.itemCount) {
                    bannerViewpager.currentItem = 0
                } else {
                    bannerViewpager.currentItem = tab
                }
            }

            categoryAdvocacy.setOnClickListener {
                val searchQuery = "SELECT * FROM recipient WHERE jobNature LIKE \"%advocacy%\""
                searchRecipientBasedOnKeyword(searchQuery, getString(R.string.category_advocacy))
            }

            categoryHealth.setOnClickListener {
                val searchQuery = "SELECT * FROM recipient WHERE jobNature LIKE \"%health%\""
                searchRecipientBasedOnKeyword(searchQuery, getString(R.string.category_health))
            }

            categoryEducation.setOnClickListener { view: View? ->
                val searchQuery = "SELECT * FROM recipient WHERE jobNature LIKE \"%education%\""
                searchRecipientBasedOnKeyword(searchQuery, getString(R.string.category_education))
            }

            categoryChildren.setOnClickListener { view: View? ->
                val searchQuery = "SELECT * FROM recipient WHERE jobNature LIKE \"%children%\""
                searchRecipientBasedOnKeyword(searchQuery, getString(R.string.category_children))
            }

            categoryEnvironment.setOnClickListener { view: View? ->
                val searchQuery = "SELECT * FROM recipient WHERE jobNature LIKE \"%environment%\""
                searchRecipientBasedOnKeyword(searchQuery, getString(R.string.category_environment))
            }

            showAllCategory.setOnClickListener {
                checkIfMayNavigate {
                    findNavController().navigate(R.id.showAllJobNatureCategoryBottomSheet)
                }
            }

            changeRecommendationCriteriaButton.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_setRecommendationCriteriaFragment)
            }

            changeRecommendationCriteriaButton2.setOnClickListener {
                findNavController().navigate(R.id.action_homeFragment_to_setRecommendationCriteriaFragment)
            }

            viewAllButton.setOnClickListener {
                val action =
                    HomeFragmentDirections.actionHomeFragmentToRecipientFilterFragment(
                        "SELECT * FROM recipient", null, null
                    )
                findNavController().navigate(action)
            }

            clearRecommendationCriteriaButton.setOnClickListener {
                lifecycleScope.launch {
                    suggestionsViewModel.clearRecommendationMetadata()
                    showShortToast("Recommendation metadata cleared")
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.crowdFundingRecyclerview.apply {
            layoutManager = ScaleLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            setHasFixedSize(true)
            addItemDecoration(LinePagerIndicatorDecoration())
            PagerSnapHelper().attachToRecyclerView(this)
            adapter = crowdFundingPagingAdapter
        }

        binding.recommendationRecyclerview.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = recommendationAdapter
            LinearSnapHelper().attachToRecyclerView(this)
        }
    }

    private fun subscribeUI() {
        mainViewModel.bannerList.observe(
            viewLifecycleOwner,
            { bannerList ->
                if (bannerList != null && bannerList.isNotEmpty()) {
                    binding.bannerContainer.visibility = View.VISIBLE
                    bannerPagerAdapter.setBannerList(bannerList)
                } else {
                    binding.bannerContainer.visibility = View.GONE
                }
            }
        )

        mainViewModel.crowdFundingList.observe(
            viewLifecycleOwner,
            { crowdFundingList ->
                binding.crowdFundingRecyclerview.scrollToPosition(0)
                crowdFundingPagingAdapter?.submitData(
                    viewLifecycleOwner.lifecycle,
                    crowdFundingList
                )
            }
        )

        crowdFundingPagingAdapter?.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val itemCount = crowdFundingPagingAdapter!!.itemCount
                    val isEmpty = itemCount <= 0

                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        binding.crowdfundingText.visibility = View.GONE
                        binding.crowdfundingSubtext.visibility = View.GONE
                        binding.crowdFundingRecyclerview.visibility = View.GONE
                    } else {
                        binding.crowdfundingText.visibility = View.VISIBLE
                        binding.crowdfundingSubtext.visibility = View.VISIBLE
                        binding.crowdFundingRecyclerview.visibility = View.VISIBLE
                    }
                }
                LoadState.Loading -> {
                }
                is LoadState.Error -> {
                    val error = when {
                        loadState.prepend is LoadState.Error -> loadState.prepend as LoadState.Error
                        loadState.append is LoadState.Error -> loadState.append as LoadState.Error
                        loadState.refresh is LoadState.Error -> loadState.refresh as LoadState.Error
                        else -> null
                    }
                    error?.let { Timber.d("Paging Load Failed: ${it.error.message}") }
                }
            }
        }

        suggestionsViewModel.recommendationList.observe(
            viewLifecycleOwner,
            { recipientList ->
                if (recipientList.isEmpty()) {
                    onEmptyRecommendationList()
                } else {
                    binding.recommendationRecyclerview.scrollToPosition(0)
                    binding.noItemFoundContainer.visibility = View.GONE
                    binding.contentContainer.visibility = View.VISIBLE
                    recommendationAdapter?.submitList(recipientList)
                }
            }
        )
    }

    private fun searchRecipientBasedOnKeyword(query: String, searchKeyword: String) {
        val action = HomeFragmentDirections.actionHomeFragmentToRecipientFilterFragment(
            query, searchKeyword, null
        )
        Navigation.findNavController(requireView()).navigate(action)
    }

    private fun navigateToRecipientBottomSheet(recipient: Recipient) {
        val action =
            HomeFragmentDirections.actionHomeFragmentToRecipientBottomSheet(
                recipient
            )
        checkIfMayNavigate { findNavController().navigate(action) }
    }

    private fun getDirectionsToRecipientLocation(recipient: Recipient) {
        val latitude = recipient.latitude
        val longitude = recipient.longitude
        val gmmIntentUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)

        if (mapIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivity(mapIntent)
        } else {
            showShortToast("No application found to be able to open up a map")
        }
    }

    private fun shareRecipient(recipient: Recipient) {
        val sharingIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                getShareRecipientContentMessage(recipient).toString()
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

        var recipientNumOfSocialMediaCounter = 0
        val tempMessageHolder = StringBuilder()

        val instagram = recipient.instagram
        if (instagram.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nInstagram: $instagram")
        }

        val linkedin = recipient.linkedin
        if (linkedin.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nLinkedIn: $linkedin")
        }

        val facebook = recipient.facebook
        if (facebook.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nFacebook: $facebook")
        }

        val twitter = recipient.twitter
        if (twitter.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nTwitter: $twitter")
        }

        val vimeo = recipient.vimeo
        if (vimeo.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nVimeo: $vimeo")
        }

        val youtube = recipient.youtube
        if (youtube.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nYoutube: $youtube")
        }

        val pinterest = recipient.pinterest
        if (pinterest.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nPinterest: $pinterest")
        }

        val flickr = recipient.flickr
        if (flickr.isNotEmpty()) {
            recipientNumOfSocialMediaCounter += 1
            tempMessageHolder.append("\nFlickr: $flickr")
        }

        if (recipientNumOfSocialMediaCounter > 0) {
            message.append("\n")
        }
        return message.append(tempMessageHolder.toString())
    }

    private fun onEmptyRecommendationList() {
        binding.contentContainer.visibility = View.GONE

        val currentUserID = currentFirebaseUser?.uid
        val previousLocationCriteria =
            suggestionsViewModel.getPreviousLocationRecommendationCriteria(
                currentUserID
            )
        val previousJobNatureCriteria =
            suggestionsViewModel.getPreviousJobNatureRecommendationCriteria(
                currentUserID
            )

        if (previousJobNatureCriteria.isNullOrEmpty() or previousLocationCriteria.isNullOrEmpty()) {
            onNoPreviousRecommendationCriteriaFound()
        } else {
            onPreviousRecommendationCriteriaFound(
                previousLocationCriteria,
                previousJobNatureCriteria
            )
        }
        binding.noItemFoundContainer.visibility = View.VISIBLE
    }

    private fun onNoPreviousRecommendationCriteriaFound() {
        binding.previousJobNatureCriteriaTitle.visibility = View.GONE
        binding.previousJobNatureCriteria.visibility = View.GONE
        binding.previousLocationCriteriaTitle.visibility = View.GONE
        binding.previousLocationCriteria.visibility = View.GONE
        binding.noPreviousRecommendationCriteria.visibility = View.VISIBLE
    }

    private fun onPreviousRecommendationCriteriaFound(
        previousLocationCriteria: String?,
        previousJobNatureCriteria: String?
    ) {
        binding.previousJobNatureCriteria.text = previousJobNatureCriteria
        binding.previousLocationCriteria.text = previousLocationCriteria
        binding.noPreviousRecommendationCriteria.visibility = View.GONE
        binding.previousJobNatureCriteriaTitle.visibility = View.VISIBLE
        binding.previousJobNatureCriteria.visibility = View.VISIBLE
        binding.previousLocationCriteriaTitle.visibility = View.VISIBLE
        binding.previousLocationCriteria.visibility = View.VISIBLE
    }

    private fun showRecommendationGenerationInfo() {
        createBalloon(requireContext()) {
            setArrowSize(10)
            setWidth(BalloonSizeSpec.WRAP)
            setHeight(BalloonSizeSpec.WRAP)
            setPadding(15)
            setArrowPosition(0.5f)
            setCornerRadius(4f)
            setText(getString(R.string.recommendation_generation_info))
            setTextColorResource(R.color.dimgray)
            setTextGravity(0)
            setBackgroundColorResource(android.R.color.white)
            setBalloonAnimation(BalloonAnimation.OVERSHOOT)
            setLifecycleOwner(lifecycleOwner)
        }.apply {
            setOnBalloonOutsideTouchListener { view, motionEvent ->
                dismiss()
            }
            setOnBalloonClickListener {
                dismiss()
            }
        }.showAlignBottom(binding.recommendationGenerationInfo)
    }

    private fun reportUrlLinkError(link: String) {
        val data = mutableMapOf<String, Any?>()
        data["title"] = "Social Media Link Error"
        data["message"] = "Link: $link"
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
