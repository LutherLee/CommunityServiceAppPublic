package com.example.communityserviceapp.ui.profile

import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.repository.detachFirebaseRealTimeListenerThatDependsOnUserID
import com.example.communityserviceapp.databinding.ProfileFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.bottomSheet.onBottomSheetDismissListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    @Inject lateinit var sharedPreferences: SharedPreferences
    @Inject lateinit var editor: SharedPreferences.Editor
    private var isLoginWithGoogleSignIn = false
    private var isUserAlreadyLogin = false
    private lateinit var binding: ProfileFragmentBinding

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshUser(this.javaClass.simpleName)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ProfileFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onDestroy() {
        onBottomSheetDismissListener = null
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        subscribeUI()
        setupListeners()
        setupSwipeRefreshColorScheme(binding.swipeRefreshLayout)
    }

    private fun subscribeUI() {
        mainViewModel.currentUser.observe(
            viewLifecycleOwner,
            {
                isUserAlreadyLogin =
                    sharedPreferences.getBoolean(getString(R.string.is_user_already_login), false)

                if (it != null && isUserAlreadyLogin) {
                    binding.loadingUserDetailsContainer.visibility = View.GONE
                    updateUIWithCurrentUserInfo(it)
                }
                // There is a delay in observing livedata value, so if user already login
                // don't show the default (not logged in) profile layout
                else if (!isUserAlreadyLogin) {
                    binding.loadingUserDetailsContainer.visibility = View.GONE
                    showDefaultProfileLayout()
                }
            }
        )
    }

    private fun setupListeners() {
        onBottomSheetDismissListener = { result ->
            mainViewModel.refreshUser(this.javaClass.simpleName)
            when (result) {
                is Result.Success -> {
                    /**
                     *  Must run in a coroutine since the listener may be invoked from another
                     *  coroutine to prevent "CalledFromWrongThreadException"
                     *  @See: UploadPictureOptionBottomSheet implementation
                     */
                    lifecycleScope.launch {
                        updateUIWithCurrentUserInfo(currentFirebaseUser!!)
                        val data = result.data as String?
                        if (!data.isNullOrEmpty()) {
                            showBottomNavAnchoredSnackbar(data)
                        }
                    }
                }
                is Result.Error -> {
                    val error = result.error
                    if (error.isNotEmpty()) {
                        showBottomNavAnchoredSnackbar(error, 3000)
                    }
                }
            }
        }

        binding.apply {
            loginButton.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            }

            logoutButton.setOnClickListener { showConfirmSignOutAlertDialog() }

            changeUsernameButton.setOnClickListener {
                checkIfMayNavigate(R.id.action_profileFragment_to_changeUsernameBottomSheet)
            }

            editProfilePictureButton.setOnClickListener {
                checkIfMayNavigate(R.id.action_profileFragment_to_uploadPictureOptionBottomSheet)
            }

            updateEmailAddressButton.setOnClickListener {
                checkIfMayNavigate(R.id.action_profileFragment_to_updateEmailAddressBottomSheet)
            }

            updatePasswordButton.setOnClickListener {
                checkIfMayNavigate(R.id.action_profileFragment_to_updatePasswordBottomSheet)
            }

            appSettings.announcementButton.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_announcementFragment)
            }

            appSettings.FAQButton.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_faqFragment)
            }

            appSettings.feedbackButton.setOnClickListener {
                val action = ProfileFragmentDirections.actionProfileFragmentToFeedbackFragment(
                    currentFirebaseUser?.email,
                    null
                )
                findNavController().navigate(action)
            }

            swipeRefreshLayout.setOnRefreshListener {
                currentFirebaseUser?.let {
                    updateUIWithCurrentUserInfo(it)
                }
                swipeRefreshLayout.isRefreshing = false
            }

            loggedInProfilePictureImage.setOnClickListener {
                hideBottomNavigationView()
                findNavController().navigate(R.id.action_profileFragment_to_imageViewDetailBottomSheet)
            }
        }
    }

    private fun updateUIWithCurrentUserInfo(user: FirebaseUser) {
        isLoginWithGoogleSignIn =
            sharedPreferences.getBoolean(getString(R.string.login_with_google_sign_in), false)

        binding.apply {
            accountUsername.text = user.displayName
            accountEmail.text = user.email
            accountCreatedOn.text = SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.ROOT
            ).format(Date(user.metadata!!.creationTimestamp))
        }
        loadProfilePictureWithDefaultSetting(user.photoUrl)
        showLoggedInProfileLayout()
    }

    private fun showConfirmSignOutAlertDialog() {
        binding.logoutButton.isEnabled = false // Disable to prevent fast double click

        MaterialAlertDialogBuilder(requireContext(), R.style.DefaultAlertDialogTheme)
            .setTitle(getString(R.string.confirm_logout_title))
            .setMessage(getString(R.string.confirm_logout_message))
            .setPositiveButton("Confirm") { dialog: DialogInterface?, which: Int -> signUserOut() }
            .setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
                binding.logoutButton.isEnabled = true
                dialog.dismiss()
            }
            .setCancelable(false)
            .create().show()
    }

    private fun signUserOut() {
        detachFirebaseRealTimeListenerThatDependsOnUserID()
        signOutOfFirebase()
        getGoogleSignInClient().signOut() // Google account Sign out
        mainViewModel.refreshUser(this.javaClass.simpleName)
        binding.logoutButton.isEnabled = true
    }

    private fun showLoggedInProfileLayout() {
        binding.apply {
            loggedInProfile.visibility = View.VISIBLE
            myAccountSettings.visibility = View.VISIBLE
            if (!isLoginWithGoogleSignIn) {
                updateAccountDetailContainer.visibility = View.VISIBLE
            }
            appSettings.root.visibility = View.VISIBLE
            defaultProfile.visibility = View.GONE
            swipeRefreshLayout.isEnabled = true
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun showDefaultProfileLayout() {
        binding.apply {
            loggedInProfile.visibility = View.GONE
            myAccountSettings.visibility = View.GONE
            defaultProfile.visibility = View.VISIBLE
            appSettings.root.visibility = View.VISIBLE
            swipeRefreshLayout.isEnabled = false
        }
    }

    private fun loadProfilePictureWithDefaultSetting(uri: Uri?) {
        Glide.with(requireContext())
            .load(uri)
            .circleCrop()
            .thumbnail(0.25f)
            .placeholder(getCircularProgressDrawable(requireContext()))
            .error(R.drawable.custom_account_circle)
            .into(binding.loggedInProfilePictureImage)
    }
}
