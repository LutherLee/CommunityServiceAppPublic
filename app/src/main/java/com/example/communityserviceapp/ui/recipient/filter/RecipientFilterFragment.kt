package com.example.communityserviceapp.ui.recipient.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.RecipientFilterFragmentBinding
import com.example.communityserviceapp.ui.adapters.RecipientItemPagingAdapter
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_FILTER
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RecipientFilterFragment : Fragment() {

    private val recipientFilterViewModel: RecipientFilterViewModel by viewModels()
    @Inject
    lateinit var filterRecipientDrawerLayout: FilterRecipientDrawerLayout
    @Inject
    lateinit var recipientItemPagingAdapter: RecipientItemPagingAdapter
    private lateinit var binding: RecipientFilterFragmentBinding
    private val args: RecipientFilterFragmentArgs by navArgs()
    private var lastFilterRecipientSQLQuery: String? = null
    private var lastSearchKeyword: String? = null
    private var lastFilterKeywords: Array<String>? = null
    private var isShowingAllRecipient = false
    private var recyclerviewScrollPosition = 0
    private var isFilterChanging = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                        binding.drawerLayout.closeDrawer(GravityCompat.END)
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
        binding = RecipientFilterFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkIfUserMadeQueryBefore()
        hideBottomNavigationView()
        setupDrawerLayout()
        setupRecyclerView()
        subscribeUI()
        setupListeners()
        setupActionBar()
    }

    override fun onDestroyView() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(null)
        recipientItemPagingAdapter.onRecipientItemChipClickListener = null
        super.onDestroyView()
    }

    private fun setupActionBar() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun checkIfUserMadeQueryBefore() {
        when {
            // User made a search before and return to this fragment
            lastSearchKeyword != null -> {
                checkIfCurrentListIsShowingAllRecipient()
            }
            // User made a filter before and return to this fragment
            lastFilterKeywords != null -> {
                onFilterRecipient()
            }
            // No search or filter made before
            else -> {
                checkUserMadeQueryFromWhere()
            }
        }
    }

    private fun checkIfCurrentListIsShowingAllRecipient() {
        binding.apply {
            if (isShowingAllRecipient) {
                searchTitle.text = lastSearchKeyword
                clearFilterText.visibility = View.GONE
            } else {
                searchTitle.append(" \"$lastSearchKeyword\"")
            }
            searchTitle.visibility = View.VISIBLE
        }
    }

    private fun checkUserMadeQueryFromWhere() {
        val sqlQuery = requireArguments().get("SQLQuery") as String?
        if (sqlQuery.isNullOrEmpty()) {
            // Query made from HomeFragment via Bottom Navigation View
            recipientFilterViewModel.setFilterRecipientSQLQuery("SELECT * FROM recipient")
            binding.searchTitle.text = getString(R.string.default_search_title)
            binding.clearFilterText.visibility = View.GONE
            binding.searchAndFilterItemCount.text =
                getString(R.string.filter_item_count, recipientItemPagingAdapter.itemCount)
            binding.searchTitle.visibility = View.VISIBLE
        } else if (lastFilterRecipientSQLQuery == null) {
            // Query made from ShowAllJobNatureCategoryBottomSheet, 
            // HomeFragment (Categories) or RecipientFavoriteFragment
            lastFilterRecipientSQLQuery = args.SQLQuery
            lastSearchKeyword = args.searchKeyword
            lastFilterKeywords = args.filterKeywords
            recipientFilterViewModel.setFilterRecipientSQLQuery(lastFilterRecipientSQLQuery!!)
            isShowingAllRecipient = false
            appendSearchTitleOrInflateFilterChipGroup()
        }
    }

    private fun appendSearchTitleOrInflateFilterChipGroup() {
        binding.apply {
            if (lastSearchKeyword != null) {
                // Query made from ShowAllJobNatureCategoryBottomSheet or HomeFragment (Categories)
                searchTitle.append(" \"$lastSearchKeyword\"")
                binding.searchAndFilterItemCount.text =
                    getString(R.string.filter_item_count, recipientItemPagingAdapter.itemCount)
                binding.searchTitle.visibility = View.VISIBLE
            } else {
                // Query made from RecipientFavoriteFragment
                searchTitle.text = getString(R.string.default_search_title)
                clearFilterText.visibility = View.GONE
                binding.searchAndFilterItemCount.text =
                    getString(R.string.filter_item_count, recipientItemPagingAdapter.itemCount)
                binding.searchTitle.visibility = View.VISIBLE
            }
        }
    }

    private fun setupDrawerLayout() {
        val toggle = object : ActionBarDrawerToggle(
            requireActivity(), binding.drawerLayout,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        ) {}
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        configureDrawerLayout()
    }

    private fun configureDrawerLayout() {
        filterRecipientDrawerLayout.apply {
            fillJobNatureChipGroup(requireContext(), viewLifecycleOwner, binding.jobNatureChipGroup)
            fillLocationChipGroup(requireContext(), binding.locationChipGroup)
            fillRatingChipGroup(requireContext(), binding.ratingChipGroup)
            fillReviewChipGroup(requireContext(), binding.reviewChipGroup)
        }
    }

    private fun setupRecyclerView() {
        binding.apply {
            filteredRecipientRecyclerview.itemAnimator = null
            filteredRecipientRecyclerview.layoutManager = LinearLayoutManager(requireContext())
            filteredRecipientRecyclerview.adapter = recipientItemPagingAdapter
        }
    }

    private fun subscribeUI() {
        recipientFilterViewModel.pagedFilteredRecipientItemList.observe(
            viewLifecycleOwner,
            { pagingData ->
                recipientItemPagingAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
            }
        )

        recipientItemPagingAdapter.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val itemCount = recipientItemPagingAdapter.itemCount
                    val isEmpty = itemCount <= 0

                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        onEmptyFilterResult()
                    } else if (!isEmpty && isFilterChanging) {
                        onFilterResultFound(itemCount)
                    }
                    binding.searchAndFilterItemCount.text =
                        getString(R.string.filter_item_count, itemCount)
                }
                LoadState.Loading -> {
                    binding.noResultFound.visibility = View.GONE
                    binding.progressBar.visibility = View.VISIBLE
                    isFilterChanging = true
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
    }

    private fun setupListeners() {
        recipientItemPagingAdapter.onRecipientItemChipClickListener = { chipText ->
            binding.filterTitle.visibility = View.GONE
            binding.searchTitle.visibility = View.VISIBLE
            binding.clearFilterText.visibility = View.VISIBLE
            val query = generateRecipientSearchQuery(chipText)
            recipientFilterViewModel.setFilterRecipientSQLQuery(query)
            binding.searchTitle.text = generateSearchTitle(chipText)
            lastSearchKeyword = chipText
            filterRecipientDrawerLayout.clearCheckForAllChipGroup(
                binding.jobNatureChipGroup,
                binding.locationChipGroup,
                binding.ratingChipGroup,
                binding.reviewChipGroup
            )
            isShowingAllRecipient = false
        }

        binding.apply {
            filterResetButton.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.END)
                resetSearchAndFilter()
            }

            filterButton.setOnClickListener {
                lastFilterRecipientSQLQuery = filterRecipientDrawerLayout.generateFilterSQLQuery(
                    binding.ratingChipGroup,
                    binding.locationChipGroup,
                    binding.jobNatureChipGroup,
                    binding.reviewChipGroup
                )
                if (lastFilterRecipientSQLQuery == null) {
                    showShortToast(requireContext(), "Please select at least one filter criteria")
                } else {
                    lastFilterKeywords = filterRecipientDrawerLayout.generateFilterKeywords(
                        binding.ratingChipGroup,
                        binding.locationChipGroup,
                        binding.jobNatureChipGroup,
                        binding.reviewChipGroup
                    )
                    recipientFilterViewModel.setFilterRecipientSQLQuery(
                        lastFilterRecipientSQLQuery!!
                    )
                    onFilterRecipient()
                }
            }

            clearFilterText.setOnClickListener { resetSearchAndFilter() }

            searchButton.setOnClickListener {
                findNavController().navigate(R.id.action_recipientFilterFragment_to_recipientSearchFragment)
            }

            favoriteButton.setOnClickListener {
                findNavController().navigate(
                    R.id.action_recipientFilterFragment_to_recipientFavoriteFragment
                )
            }

            drawerlayoutButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }

            filteredRecipientRecyclerview.addOnScrollListener(object :
                    RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}

                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            recyclerviewScrollPosition =
                                filteredRecipientRecyclerview.computeVerticalScrollOffset()
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
                filteredRecipientRecyclerview.scrollToPosition(0)
                appBarLayout.setExpanded(true)
                scrollToTopFab.hide()
            }
        }
    }

    private fun inflateFilterChipGroup() {
        binding.filterChipGroup.removeAllViews()
        for (filterKeyword in lastFilterKeywords!!) {
            val chip =
                inflateChip(CHIP_TYPE_FILTER, filterKeyword, requireContext(), isClickable = false)
            binding.filterChipGroup.addView(chip)
        }
    }

    private fun resetSearchAndFilter() {
        binding.searchTitle.text = getString(R.string.default_search_title)
        lastSearchKeyword = getString(R.string.default_search_title)
        binding.filterTitle.visibility = View.GONE
        binding.clearFilterText.visibility = View.GONE
        binding.searchTitle.visibility = View.VISIBLE
        filterRecipientDrawerLayout.clearCheckForAllChipGroup(
            binding.jobNatureChipGroup,
            binding.locationChipGroup,
            binding.ratingChipGroup,
            binding.reviewChipGroup
        )
        recipientFilterViewModel.setFilterRecipientSQLQuery(getString(R.string.show_all_recipient_query))
        isShowingAllRecipient = true
    }

    private fun generateRecipientSearchQuery(recipientName: String): String {
        return (
            "SELECT * FROM recipient WHERE name LIKE \"%" + recipientName + "%\" " +
                "OR address LIKE \"%" + recipientName + "%\" " +
                "OR jobNature LIKE \"%" + recipientName + "%\" " +
                "OR state LIKE \"%" + recipientName + "%\""
            )
    }

    private fun generateSearchTitle(recipientName: String) = "Searching for \"$recipientName\""

    private fun onFilterRecipient() {
        inflateFilterChipGroup()
        binding.clearFilterText.visibility = View.VISIBLE
        binding.searchTitle.visibility = View.GONE
        binding.filterTitle.visibility = View.VISIBLE
        lastSearchKeyword = null
        binding.drawerLayout.closeDrawer(GravityCompat.END)
        binding.horizontalChipGroupScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT)
        isShowingAllRecipient = false
    }

    private fun onEmptyFilterResult() {
        binding.progressBar.visibility = View.GONE
        binding.scrollToTopFab.hide()
        binding.noResultFound.visibility = View.VISIBLE
        disableCollapsingToolbarScroll(binding.collapsingToolbar)
    }

    private fun onFilterResultFound(itemCount: Int) {
        if (itemCount > 1) {
            enableCollapsingToolbarScroll(binding.collapsingToolbar) {
                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
            }
        } else {
            disableCollapsingToolbarScroll(binding.collapsingToolbar)
        }

        binding.progressBar.visibility = View.GONE
        binding.noResultFound.visibility = View.GONE
        binding.filteredRecipientRecyclerview.scrollToPosition(0)
        binding.appBarLayout.setExpanded(true)
        binding.scrollToTopFab.hide()
        isFilterChanging = false
    }
}
