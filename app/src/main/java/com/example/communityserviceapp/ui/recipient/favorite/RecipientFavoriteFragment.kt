package com.example.communityserviceapp.ui.recipient.favorite

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.RecipientFavoriteFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.ui.adapters.RecipientFavoritePagingAdapter
import com.example.communityserviceapp.util.*
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RecipientFavoriteFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val recipientFavoriteViewModel: RecipientFavoriteViewModel by viewModels()
    private lateinit var binding: RecipientFavoriteFragmentBinding

    @Inject
    lateinit var recipientFavoritePagingAdapter: RecipientFavoritePagingAdapter

    @Inject
    lateinit var sharedPreferences: SharedPreferences
    private var recyclerviewScrollPosition = 0
    private var isSearching = false
    private var isUserAlreadyLogin = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RecipientFavoriteFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            mainViewmodel = mainViewModel
            recipientFavoriteViewmodel = recipientFavoriteViewModel
        }
        setSoftInputModeToAdjustResize()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActionBar()
        setupRecyclerview()
        setupListener()
        subscribeUI()
        setupSwipeRefreshColorScheme(binding.swipeRefreshLayout)
    }

    override fun onDestroy() {
        recipientFavoritePagingAdapter.onRecipientItemClickListener = null
        (requireActivity() as AppCompatActivity).setSupportActionBar(null)
        super.onDestroy()
    }

    private fun setupActionBar() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupRecyclerview() {
        binding.apply {
            favoriteRecipientRecyclerview.layoutManager = LinearLayoutManager(requireContext())
            favoriteRecipientRecyclerview.adapter = recipientFavoritePagingAdapter
        }
        enableSwipeToDeleteAndUndo()
    }

    private fun setupListener() {
        recipientFavoritePagingAdapter.onRecipientItemClickListener = { recipient ->
            hideKeyboard()
            checkIfMayNavigate {
                val action =
                    RecipientFavoriteFragmentDirections.actionRecipientFavoriteFragmentToRecipientBottomSheet(
                        recipient
                    )
                findNavController().navigate(action)
            }
        }

        recipientFavoritePagingAdapter.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val itemCount = recipientFavoritePagingAdapter.itemCount
                    val isEmpty = itemCount <= 0

                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        onNoFavoriteFound()
                        disableCollapsingToolbarScroll(binding.collapsingToolbar)
                    } else if (!isEmpty) {
                        if (itemCount > 4) {
                            enableCollapsingToolbarScroll(binding.collapsingToolbar) {
                                AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                                    AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or
                                    AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED
                            }
                        } else {
                            disableCollapsingToolbarScroll(binding.collapsingToolbar)
                        }
                        onFavoriteFound(itemCount)
                    }
                }
                LoadState.Loading -> {
                    // Only show progress bar when first load, swipe to refresh or
                    // search query is set to empty
                    if (!isSearching) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
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

        binding.apply {
            clearTextButton.setOnClickListener { searchFavouritesEditText.text.clear() }

            showAllRecipientText.setOnClickListener { showAllRecipient() }

            loginButton.setOnClickListener {
                findNavController().navigate(R.id.action_recipientFavoriteFragment_to_loginFragment)
            }

            deleteAllFavouritesButton.setOnClickListener {
                hideKeyboard()
                showConfirmDeleteAllFavouritesAlertDialog()
            }

            showAllRecipientButton.setOnClickListener { showAllRecipient() }

            favoriteRecipientRecyclerview.addOnScrollListener(object :
                    RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}

                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            recyclerviewScrollPosition =
                                favoriteRecipientRecyclerview.computeVerticalScrollOffset()
                            if (recyclerviewScrollPosition > 1350) {
                                scrollToTopFab.show()
                            }
                        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            scrollToTopFab.hide()
                        }
                        super.onScrollStateChanged(recyclerView, newState)
                    }
                })

            scrollToTopFab.setOnClickListener {
                hideKeyboard()
                favoriteRecipientRecyclerview.scrollToPosition(0)
                appBarLayout.setExpanded(true)
                scrollToTopFab.hide()
            }

            swipeRefreshLayout.setOnRefreshListener {
                recipientFavoritePagingAdapter.refresh()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun subscribeUI() {
        mainViewModel.currentUserID.observe(
            viewLifecycleOwner,
            { userID ->
                isUserAlreadyLogin =
                    sharedPreferences.getBoolean(getString(R.string.is_user_already_login), false)

                // There is a delay in observing livedata value, so only show content if user is already login
                if (userID != null && isUserAlreadyLogin) {
                    binding.requireLoginWrapper.visibility = View.GONE
                    recipientFavoriteViewModel.currentUserID.value = userID
                } else if (!isUserAlreadyLogin) {
                    binding.requireLoginWrapper.visibility = View.VISIBLE
                }
            }
        )

        recipientFavoriteViewModel.userFavoritedRecipients.observe(
            viewLifecycleOwner,
            {
                recipientFavoritePagingAdapter.submitData(viewLifecycleOwner.lifecycle, it)
            }
        )

        recipientFavoriteViewModel.searchFavouriteRecipientSearchKeyword.observe(
            viewLifecycleOwner,
            { searchKeyword ->
                isSearching = searchKeyword.isNotEmpty()
            }
        )
    }

    private fun onNoFavoriteFound() {
        binding.apply {
            progressBar.visibility = View.GONE
            deleteAllFavouritesButton.visibility = View.INVISIBLE

            if (isSearching) {
                noItemFoundContainer.visibility = View.GONE
                favoriteRecipientRecyclerview.visibility = View.GONE
                noItemFoundAnimationView.pauseAnimation()
                numOfFavourites.text = "No Result"
                searchEmptyContainer.visibility = View.VISIBLE
                searchEmptyAnimationView.resumeAnimation()
            } else {
                favoritesFoundHeader.visibility = View.GONE
                favoriteRecipientRecyclerview.visibility = View.GONE
                searchEmptyContainer.visibility = View.GONE
                searchEmptyAnimationView.pauseAnimation()
                val hasUserLoginYet = mainViewmodel?.currentUser?.value != null
                if (hasUserLoginYet) {
                    noItemFoundContainer.visibility = View.VISIBLE
                    noItemFoundAnimationView.resumeAnimation()
                }
            }
        }
    }

    private fun onFavoriteFound(itemCount: Int) {
        binding.apply {
            progressBar.visibility = View.GONE
            noItemFoundContainer.visibility = View.GONE
            noItemFoundAnimationView.pauseAnimation()
            searchEmptyContainer.visibility = View.GONE
            searchEmptyAnimationView.pauseAnimation()
            numOfFavourites.text = "$itemCount Result"
            favoritesFoundHeader.visibility = View.VISIBLE
            favoriteRecipientRecyclerview.visibility = View.VISIBLE
            if (isSearching) {
                favoriteRecipientRecyclerview.scrollToPosition(0)
                appBarLayout.setExpanded(true)
            }
            deleteAllFavouritesButton.visibility = View.VISIBLE
        }
        isSearching = false
    }

    private fun enableSwipeToDeleteAndUndo() {
        val swipeToDeleteCallback = object : SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(@NonNull viewHolder: RecyclerView.ViewHolder, i: Int) {
                val position = viewHolder.bindingAdapterPosition
                val recipient = recipientFavoritePagingAdapter.getRecipientAtPosition(position)

                lifecycleScope.launch {
                    hideKeyboard()
                    val hasConnection = withContext(Dispatchers.IO) {
                        checkConnectionStatusToFirebase()
                    }
                    if (hasConnection) {
                        unfavoriteRecipient(recipient.recipientID)
                    } else {
                        recipientFavoritePagingAdapter.notifyItemChanged(position)
                        showShortSnackbar(getString(R.string.default_network_error))
                    }
                }
            }
        }
        val itemTouchHelper = ItemTouchHelper(swipeToDeleteCallback)
        itemTouchHelper.attachToRecyclerView(binding.favoriteRecipientRecyclerview)
    }

    private suspend fun unfavoriteRecipient(recipientID: String) {
        val isSuccessful =
            recipientFavoriteViewModel.setFavoriteOrUnfavouriteRecipient(recipientID, true)
        if (isSuccessful) {
            Snackbar.make(requireView(), "Removed from favourites", Snackbar.LENGTH_SHORT).apply {
                setAction("Undo") {
                    lifecycleScope.launch {
                        recipientFavoriteViewModel.setFavoriteOrUnfavouriteRecipient(
                            recipientID,
                            false
                        )
                    }
                }
            }.show()
        } else {
            Snackbar.make(requireView(), "Unable to remove from favourites", Snackbar.LENGTH_SHORT)
                .apply {
                    setAction("Retry") {
                        lifecycleScope.launch {
                            recipientFavoriteViewModel.setFavoriteOrUnfavouriteRecipient(
                                recipientID,
                                true
                            )
                        }
                    }
                }.show()
        }
    }

    private fun showConfirmDeleteAllFavouritesAlertDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.DefaultAlertDialogTheme)
            .setTitle("Delete All Favourites")
            .setMessage("Are you sure you want to proceed?")
            .setNegativeButton("Cancel") { dialogInterface: DialogInterface, i: Int ->
                dialogInterface.dismiss()
            }
            .setPositiveButton("Confirm") { dialogInterface: DialogInterface, i: Int ->
                lifecycleScope.launch {
                    val isSuccessful = withContext(Dispatchers.IO) {
                        recipientFavoriteViewModel.deleteUserAllFavorites()
                    }
                    if (isSuccessful) {
                        binding.favoriteRecipientRecyclerview.scrollToPosition(0)
                    } else {
                        showShortSnackbar("Network or User Credential Error")
                    }
                }
            }
            .create().show()
    }

    private fun showAllRecipient() {
        hideKeyboard()
        val action =
            RecipientFavoriteFragmentDirections.actionRecipientFavoriteFragmentToRecipientFilterFragment(
                "SELECT * FROM recipient", null, null
            )
        findNavController().navigate(action)
    }
}
