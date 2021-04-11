package com.example.communityserviceapp.ui.recipient.review

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.data.repository.onRecipientReviewLoadedListener
import com.example.communityserviceapp.databinding.RecipientReviewBottomSheetBinding
import com.example.communityserviceapp.ui.adapters.RecipientReviewAdapter
import com.example.communityserviceapp.ui.recipient.RecipientViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.bottomSheet.BaseBottomSheet
import com.example.communityserviceapp.util.bottomSheet.disableBottomSheetDragDownToDismiss
import com.example.communityserviceapp.util.bottomSheet.onBottomSheetDismissListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class RecipientReviewBottomSheet : BaseBottomSheet() {
    @Inject
    lateinit var recipientReviewAdapter: RecipientReviewAdapter
    private val recipientViewModel: RecipientViewModel by viewModels()
    private lateinit var binding: RecipientReviewBottomSheetBinding
    private val args: RecipientReviewBottomSheetArgs by navArgs()
    private lateinit var currentRecipient: Recipient
    private var shouldRefreshReview = true
    private var lastSearchQueryWithoutSorting = ""
    private var defaultSearchQuery = ""
    private var recyclerviewScrollPosition = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.FullScreenBottomSheetDialogThemeSlideInAnim)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener { dialogInterface ->
                val bottomSheetDialog = dialogInterface as BottomSheetDialog
                disableBottomSheetDragDownToDismiss(bottomSheetDialog)
            }
        }
    }

    override fun onResume() {
        recipientViewModel.attachRecipientReviewRealTimeListener(currentRecipient.recipientID)
        super.onResume()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RecipientReviewBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val result = mutableMapOf<String, Any>()
        result["navigateToReviewRecipientBottomSheet"] = false
        result["shouldUpdateRecipientUI"] = true
        onBottomSheetDismissListener?.invoke(Result.Success(result))
    }

    override fun onDestroyView() {
        recipientReviewAdapter.onReviewChipClickListener = null
        onRecipientReviewLoadedListener = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentRecipient = args.recipient
        recipientViewModel.setRecipient(currentRecipient)
        lastSearchQueryWithoutSorting =
            "SELECT * FROM review WHERE reviewOn = \"${currentRecipient.recipientID}\""
        defaultSearchQuery =
            "SELECT * FROM review WHERE reviewOn = \"${currentRecipient.recipientID}\""
        setupActionBar()
        setupRecyclerview()
        setupListeners()
        subscribeUI()
        setupSwipeRefreshColorScheme(binding.swipeRefreshLayout)
    }

    private fun setupActionBar() {
        Glide.with(requireView())
            .load(currentRecipient.recipientImageUrl)
            .error(R.drawable.ic_baseline_image_grey_24dp)
            .into(binding.roundedImageView)
        val sourceString = "<i>Ratings and reviews</i><br><b>${currentRecipient.recipientName}</b>"
        binding.toolbarTextview.text = Html.fromHtml(sourceString, HtmlCompat.FROM_HTML_MODE_LEGACY)
        binding.recipientRating.text =
            String.format(Locale.ROOT, "%.1f", currentRecipient.recipientRating)
    }

    private fun setupRecyclerview() {
        binding.recipientReviewRecyclerview.apply {
            itemAnimator = null
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recipientReviewAdapter
        }
    }

    private fun setupListeners() {
        binding.apply {
            retryConnectionButton.setOnClickListener {
                lifecycleScope.launch {
                    val hasConnection = withContext(Dispatchers.IO) {
                        checkConnectionStatusToFirebase()
                    }
                    if (hasConnection) {
                        val recipientID = currentRecipient.recipientID
                        refreshCurrentRecipientInfo(recipientID)
                        // Get any modified (added or deleted) review and update UI
                        recipientViewModel.attachRecipientReviewRealTimeListener(recipientID)
                    }
                }
            }

            backButton.setOnClickListener { findNavController().navigateUp() }

            swipeRefreshLayout.setOnRefreshListener {
                binding.swipeRefreshLayout.isRefreshing = false
                binding.swipeRefreshLayout.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                val recipientID = currentRecipient.recipientID
                refreshCurrentRecipientInfo(recipientID)
                // Get any modified (added or deleted) review and update UI
                recipientViewModel.attachRecipientReviewRealTimeListener(recipientID)
            }

            filterChipGroup.setOnCheckedChangeListener { group, checkedId ->
                val chipText = group.findViewById<Chip>(checkedId).text
                filterTypeText.text = chipText
                updateDisplayReview(chipText.toString())
            }

            reviewRecipientButton.setOnClickListener {
                dismiss()
                val data = mutableMapOf<String, Any>()
                data["navigateToReviewRecipientBottomSheet"] = true
                onBottomSheetDismissListener?.invoke(Result.Success(data))
            }

            sortReviewButton.setOnClickListener { showSortingFilterAlertDialog() }

            recipientReviewRecyclerview.addOnScrollListener(object :
                    RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            recyclerviewScrollPosition =
                                recipientReviewRecyclerview.computeVerticalScrollOffset()
                            if (recyclerviewScrollPosition > 2000) {
                                scrollToTopFab.show()
                            }
                        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            scrollToTopFab.hide()
                        }
                        super.onScrollStateChanged(recyclerView, newState)
                    }
                })

            scrollToTopFab.setOnClickListener {
                appBarLayout.setExpanded(true, true)
                recipientReviewRecyclerview.scrollToPosition(0)
                scrollToTopFab.hide()
            }

            recipientReviewAdapter.onReviewChipClickListener = { chipText ->
                val chip = when (chipText) {
                    "Helpful" -> {
                        filterChipGroup.getChildAt(8)
                    }
                    "Recommended" -> {
                        filterChipGroup.getChildAt(9)
                    }
                    "Informative" -> {
                        filterChipGroup.getChildAt(10)
                    }
                    "Efficient" -> {
                        filterChipGroup.getChildAt(11)
                    }
                    "Need Improvement" -> {
                        filterChipGroup.getChildAt(12)
                    }
                    else -> {
                        filterChipGroup.getChildAt(8)
                    }
                } as Chip

                chip.isChecked = true
                binding.scrollToTopFab.hide()
                binding.horizontalChipGroupScrollView.scrollTo(chip.left, chip.right)
                binding.recipientReviewRecyclerview.scrollToPosition(0)
                binding.appBarLayout.setExpanded(true)
            }
        }
    }

    private fun subscribeUI() {
        recipientViewModel.pagedRecipientReviewList.observe(
            viewLifecycleOwner,
            {
                // Only load review once, if there is already review.
                // Since LiveData update on item change, to override this behavior we
                // use a boolean to check before proceeding.
                if (shouldRefreshReview) {
                    recipientReviewAdapter.submitData(viewLifecycleOwner.lifecycle, it)
                }
            }
        )

        onRecipientReviewLoadedListener = {
            shouldRefreshReview = true
            recipientViewModel.refreshRecipientReview()
        }

        recipientReviewAdapter.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val isEmpty = recipientReviewAdapter.itemCount <= 0

                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        // When there is no review based on filtering option, it can be due to
                        // no network connection to fetch data or no data found in local database
                        lifecycleScope.launch {
                            val hasConnection = withContext(Dispatchers.IO) {
                                checkConnectionStatusToFirebase()
                            }
                            binding.progressBar.visibility = View.GONE
                            binding.swipeRefreshLayout.visibility = View.GONE
                            binding.scrollToTopFab.visibility = View.GONE
                            if (hasConnection) {
                                val isEmpty = recipientReviewAdapter.itemCount <= 0
                                if (isEmpty) {
                                    onEmptyReviewFound()
                                }
                            } else {
                                onNoNetworkConnection()
                            }
                        }
                    } else if (!isEmpty) {
                        onReviewFound()
                    }
                }
                LoadState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is LoadState.Error -> {
                    val error = when {
                        loadState.prepend is LoadState.Error -> loadState.prepend as LoadState.Error
                        loadState.append is LoadState.Error -> loadState.append as LoadState.Error
                        loadState.refresh is LoadState.Error -> loadState.refresh as LoadState.Error
                        else -> null
                    }
                    error?.let { Timber.d("Pagination Error: ${it.error.message}") }
                }
            }
        }
    }

    private fun onEmptyReviewFound() {
        binding.noWifiContainer.visibility = View.GONE
        binding.apply {
            // Check if empty result is due to filtering option or on initial (first) load
            if (lastSearchQueryWithoutSorting == defaultSearchQuery) {
                // empty result on initial load
                contentContainer.visibility = View.GONE
                noRecipientReviewFoundContainer.visibility = View.VISIBLE
            } else {
                // empty result on filtering
                noRecipientReviewFoundContainer.visibility = View.GONE
                noFilteredRecipientReviewFoundContainer.visibility = View.VISIBLE
            }
        }
        disableCollapsingToolbarScroll(binding.collapsingToolbar)
    }

    private fun onReviewFound() {
        enableCollapsingToolbarScroll(binding.collapsingToolbar) {
            AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED or
                AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        }

        binding.apply {
            progressBar.visibility = View.GONE
            binding.noWifiContainer.visibility = View.GONE
            noRecipientReviewFoundContainer.visibility = View.GONE
            noFilteredRecipientReviewFoundContainer.visibility = View.GONE
            contentContainer.visibility = View.VISIBLE
            swipeRefreshLayout.visibility = View.VISIBLE
        }
        shouldRefreshReview = false
    }

    private fun showSortingFilterAlertDialog() {
        val sortingOrderList = arrayOf("Newest", "Oldest")

        MaterialAlertDialogBuilder(requireContext(), R.style.DefaultAlertDialogTheme)
            .setTitle("Sort By")
            .setItems(sortingOrderList) { dialog: DialogInterface?, itemPosition: Int ->
                when (itemPosition) {
                    0 -> {
                        binding.sortReviewButton.text = "Newest"
                        recipientViewModel.sortingOrder = Constants.SORT_ORDER_NEWEST
                    }
                    1 -> {
                        binding.sortReviewButton.text = "Oldest"
                        recipientViewModel.sortingOrder = Constants.SORT_ORDER_OLDEST
                    }
                    else -> {
                        // Leave Empty
                    }
                }
                refreshRecipientReviews(lastSearchQueryWithoutSorting)
            }
            .setPositiveButton("Close") { dialogInterface: DialogInterface, i: Int ->
                dialogInterface.dismiss()
            }
            .create().show()
    }

    private fun updateDisplayReview(chipText: String) {
        lastSearchQueryWithoutSorting = when (chipText) {
            "All" -> {
                defaultSearchQuery
            }
            "Positive" -> {
                "$defaultSearchQuery AND reviewRating >= 4.0"
            }
            "Negative" -> {
                "$defaultSearchQuery AND reviewRating < 3.0"
            }
            else -> {
                val isNumber = chipText.first().isDigit()
                if (isNumber) {
                    // Chip text is related to rating (5 star, 4 star, etc.)
                    "$defaultSearchQuery AND reviewRating = ${chipText.first()}"
                } else {
                    // Chip text is review tags (e.g. Helpful, Recommended, etc.)
                    "$defaultSearchQuery AND tags LIKE \"%$chipText%\""
                }
            }
        }
        refreshRecipientReviews(lastSearchQueryWithoutSorting)
    }

    private fun refreshRecipientReviews(query: String) {
        shouldRefreshReview = true
        recipientViewModel.recipientReviewSearchQuery.value = query
        binding.appBarLayout.setExpanded(true)
        recipientViewModel.refreshRecipientReview()
    }

    private fun refreshCurrentRecipientInfo(recipientID: String) {
        lifecycleScope.launch {
            currentRecipient = recipientViewModel.getRecipientItemByID(recipientID)
            setupActionBar()
        }
    }

    private fun onNoNetworkConnection() {
        binding.contentContainer.visibility = View.GONE
        binding.noFilteredRecipientReviewFoundContainer.visibility =
            View.GONE
        binding.noRecipientReviewFoundContainer.visibility = View.GONE
        binding.noWifiContainer.visibility = View.VISIBLE
    }
}
