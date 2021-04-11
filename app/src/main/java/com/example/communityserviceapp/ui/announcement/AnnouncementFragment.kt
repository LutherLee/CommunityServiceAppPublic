package com.example.communityserviceapp.ui.announcement

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.AnnouncementFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.ui.adapters.AnnouncementPagingAdapter
import com.example.communityserviceapp.util.currentFirebaseUser
import com.example.communityserviceapp.util.setupSwipeRefreshColorScheme
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AnnouncementFragment : Fragment() {

    private val announcementViewModel: AnnouncementViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: AnnouncementFragmentBinding
    @Inject lateinit var announcementPagingAdapter: AnnouncementPagingAdapter
    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var editor: SharedPreferences.Editor
    private var isAnnouncementNotificationEnabled = false
    private var recyclerviewScrollPosition = 0
    private var toast: Toast? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AnnouncementFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshUser(this.javaClass.simpleName)
    }

    override fun onDestroyView() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(null)
        toast?.cancel()
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActionBar()
        setupRecyclerview()
        checkNotificationSettingsStatus()
        subscribeUI()
        setupListeners()
        setupSwipeRefreshColorScheme(binding.swipeRefreshLayout)
    }

    private fun setupActionBar() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupRecyclerview() {
        binding.apply {
            announcementRecyclerview.layoutManager = LinearLayoutManager(requireContext())
            announcementRecyclerview.adapter = announcementPagingAdapter
        }
    }

    // Check if previously announcement notification setting has been disabled
    private fun checkNotificationSettingsStatus() {
        val currentUser = currentFirebaseUser
        val userDisableAnnouncementNotifications = if (currentUser == null) {
            sharedPreferences.getBoolean(
                getString(R.string.user_disable_announcement_notifications), false
            )
        } else {
            sharedPreferences.getBoolean(
                currentUser.uid + getString(R.string.user_disable_announcement_notifications),
                false
            )
        }

        if (!userDisableAnnouncementNotifications) {
            setAnnouncementSettingIconAsEnabled()
        } else {
            setAnnouncementSettingIconAsDisabled()
        }
    }

    private fun subscribeUI() {
        announcementViewModel.pagedAnnouncementList.observe(
            viewLifecycleOwner,
            { pagingData ->
                announcementPagingAdapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
            }
        )

        announcementPagingAdapter.addLoadStateListener { loadState ->
            when (loadState.refresh) {
                is LoadState.NotLoading -> {
                    val isEmpty = announcementPagingAdapter.itemCount <= 0

                    if (loadState.append.endOfPaginationReached && isEmpty) {
                        binding.progressBar.visibility = View.GONE
                        binding.announcementRecyclerview.visibility = View.GONE
                        binding.noAnnouncementFound.visibility = View.VISIBLE
                    } else if (!isEmpty) {
                        binding.progressBar.visibility = View.GONE
                        binding.noAnnouncementFound.visibility = View.GONE
                        binding.announcementRecyclerview.visibility = View.VISIBLE
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
                    error?.let { Timber.d("Paging Load Failed: ${it.error.message}") }
                }
            }
        }
    }

    private fun setupListeners() {
        binding.manageNotificationsButton.setOnClickListener {
            val currentUser = currentFirebaseUser
            if (isAnnouncementNotificationEnabled) {
                disableAnnouncementNotification(currentUser)
            } else {
                enableAnnouncementNotification(currentUser)
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            announcementPagingAdapter.refresh()
            binding.swipeRefreshLayout.isRefreshing = false
        }

        binding.announcementRecyclerview.addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        recyclerviewScrollPosition =
                            binding.announcementRecyclerview.computeVerticalScrollOffset()
                        if (recyclerviewScrollPosition > 2000) {
                            binding.scrollToTopFab.show()
                        }
                    } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        binding.scrollToTopFab.hide()
                    }
                    super.onScrollStateChanged(recyclerView, newState)
                }
            })

        binding.scrollToTopFab.setOnClickListener {
            binding.announcementRecyclerview.scrollToPosition(0)
            binding.scrollToTopFab.hide()
        }
    }

    private fun disableAnnouncementNotification(currentUser: FirebaseUser?) {
        if (currentUser == null) {
            editor.putBoolean(
                getString(R.string.user_disable_announcement_notifications),
                true
            ).commit()
        } else {
            editor.putBoolean(
                currentUser.uid + getString(R.string.user_disable_announcement_notifications),
                true
            ).commit()
        }
        setAnnouncementSettingIconAsDisabled()
        showToast("You will stop receiving announcement notification")
    }

    private fun enableAnnouncementNotification(currentUser: FirebaseUser?) {
        if (currentUser == null) {
            editor.putBoolean(
                getString(R.string.user_disable_announcement_notifications),
                false
            ).commit()
        } else {
            editor.putBoolean(
                currentUser.uid + getString(R.string.user_disable_announcement_notifications),
                false
            ).commit()
        }
        setAnnouncementSettingIconAsEnabled()
        showToast("You will be receiving announcement notification")
    }

    private fun setAnnouncementSettingIconAsEnabled() {
        isAnnouncementNotificationEnabled = true
        binding.manageNotificationsButton.setIconResource(R.drawable.ic_baseline_notifications_on_blue_24dp)
        binding.manageNotificationsButton.setIconTintResource(R.color.bright_background_blue)
    }

    private fun setAnnouncementSettingIconAsDisabled() {
        isAnnouncementNotificationEnabled = false
        binding.manageNotificationsButton.setIconResource(R.drawable.ic_baseline_notifications_off_grey_24dp)
        binding.manageNotificationsButton.setIconTintResource(R.color.gray)
    }

    private fun showToast(message: String) {
        toast?.cancel() // Cancel any toast currently showing
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast!!.show()
    }
}
