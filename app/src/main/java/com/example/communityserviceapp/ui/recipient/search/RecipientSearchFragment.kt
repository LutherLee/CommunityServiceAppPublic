package com.example.communityserviceapp.ui.recipient.search

import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import com.example.communityserviceapp.data.model.Recipient
import com.example.communityserviceapp.databinding.RecipientSearchFragmentBinding
import com.example.communityserviceapp.ui.adapters.RecipientSearchPagingAdapter
import com.example.communityserviceapp.ui.adapters.RecipientSearchSuggestionsPagingAdapter
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_FILTER
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class RecipientSearchFragment : Fragment() {

    private val recipientSearchViewModel: RecipientSearchViewModel by viewModels()
    private var recipientSearchPagingAdapter: RecipientSearchPagingAdapter? = null
    private var recipientSearchSuggestionsPagingAdapter: RecipientSearchSuggestionsPagingAdapter? = null
    lateinit var binding: RecipientSearchFragmentBinding
    private var isSearchQueryChanging = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RecipientSearchFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            searchViewmodel = recipientSearchViewModel
        }
        setSoftInputModeToAdjustResize()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fillSearchKeywordChipGroup()
        recipientSearchPagingAdapter = RecipientSearchPagingAdapter()
        recipientSearchSuggestionsPagingAdapter = RecipientSearchSuggestionsPagingAdapter()
        setupListeners()
        subscribeUI()
        setupRecyclerView()
        requestSearchEditTextFocus()
    }

    private fun requestSearchEditTextFocus() {
        binding.searchEditText.requestFocus()
        showKeyboard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recipientSearchPagingAdapter?.onRecipientItemClickListener = null
        recipientSearchSuggestionsPagingAdapter?.onRecipientItemClickListener
        recipientSearchPagingAdapter = null
        recipientSearchSuggestionsPagingAdapter = null
    }

    private fun setupRecyclerView() {
        binding.searchRecipientRecyclerview.apply {
            itemAnimator = null
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recipientSearchPagingAdapter
        }

        binding.searchSuggestionsRecyclerview.apply {
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = recipientSearchSuggestionsPagingAdapter

            val snapHelper = LinearSnapHelper()
            snapHelper.attachToRecyclerView(this)
        }
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener {
            hideKeyboard()
            findNavController().navigateUp()
        }

        recipientSearchPagingAdapter?.onRecipientItemClickListener =
            { navigateToRecipientBottomSheet(it) }

        recipientSearchSuggestionsPagingAdapter?.onRecipientItemClickListener = {
            navigateToRecipientBottomSheet(it)
        }

        binding.clearTextButton.setOnClickListener {
            recipientSearchViewModel.searchKeyword.value = ""
            onSearchResultFound()
        }

        binding.searchEditText.setOnEditorActionListener { textView, actionID, keyEvent ->
            return@setOnEditorActionListener if (actionID == EditorInfo.IME_ACTION_DONE) {
                binding.searchEditText.clearFocus()
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    private fun subscribeUI() {
        recipientSearchViewModel.searchResult.observe(
            viewLifecycleOwner,
            { pagingData ->
                recipientSearchPagingAdapter?.submitData(viewLifecycleOwner.lifecycle, pagingData)
            }
        )

        recipientSearchPagingAdapter?.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val isEmpty = recipientSearchPagingAdapter?.itemCount == 0

                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        onSearchResultEmpty()
                    } else if (isSearchQueryChanging && !isEmpty) {
                        binding.searchRecipientRecyclerview.scrollToPosition(0)
                        isSearchQueryChanging = false
                        onSearchResultFound()
                    }
                    // When navigating back from other fragment (e.g. login fragment)
                    else if (!isEmpty) {
                        onSearchResultFound()
                    }
                }
                LoadState.Loading -> {
                    isSearchQueryChanging = true
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

        recipientSearchViewModel.searchSuggestions.observe(
            viewLifecycleOwner,
            { pagingData ->
                recipientSearchSuggestionsPagingAdapter?.submitData(
                    viewLifecycleOwner.lifecycle,
                    pagingData
                )
            }
        )

        recipientSearchSuggestionsPagingAdapter?.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val isEmpty = recipientSearchSuggestionsPagingAdapter?.itemCount == 0
                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        binding.searchSuggestionsTitle.visibility = View.GONE
                        binding.searchSuggestionsRecyclerview.visibility = View.GONE
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
    }

    private fun onSearchResultEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.searchRecipientRecyclerview.visibility = View.GONE
        binding.searchEmptyContainer.visibility = View.VISIBLE
        binding.searchEmptyAnimationView.resumeAnimation()
    }

    private fun onSearchResultFound() {
        binding.progressBar.visibility = View.GONE
        binding.searchEmptyContainer.visibility = View.GONE
        binding.searchEmptyAnimationView.pauseAnimation()
        binding.searchRecipientRecyclerview.visibility = View.VISIBLE
    }

    private fun fillSearchKeywordChipGroup() {
        addLocationChipsToChipGroup()
        addJobNatureChipsToChipGroup()
    }

    private fun addLocationChipsToChipGroup() {
        val locationChipIterator = recipientSearchViewModel.getLocationChips()
        while (locationChipIterator.hasNext()) {
            val chip = inflateChip(
                CHIP_TYPE_FILTER,
                locationChipIterator.next().toString(),
                requireContext()
            )
            setChipListener(chip)
            binding.searchKeywordChipGroup.addView(chip)
        }
    }

    private fun addJobNatureChipsToChipGroup() {
        recipientSearchViewModel.jobNatureRepository.getAllJobNature().observe(
            viewLifecycleOwner,
            { jobNatureList ->
                if (!jobNatureList.isNullOrEmpty()) {
                    for (jobNature in jobNatureList) {
                        val chip = inflateChip(CHIP_TYPE_FILTER, jobNature.type, requireContext())
                        setChipListener(chip)
                        binding.searchKeywordChipGroup.addView(chip)
                    }
                }
            }
        )
    }

    private fun setChipListener(chip: Chip) {
        chip.setOnClickListener {
            recipientSearchViewModel.searchKeyword.value = chip.text.toString()
            onSearchResultFound()
            hideKeyboard()
            binding.searchEditText.clearFocus()
            chip.isChecked = false
        }
    }

    private fun navigateToRecipientBottomSheet(recipient: Recipient) {
        checkIfMayNavigate {
            hideKeyboard()
            val action =
                RecipientSearchFragmentDirections.actionRecipientSearchFragmentToRecipientBottomSheet(
                    recipient
                )
            findNavController().navigate(action)
        }
    }
}
