package com.example.communityserviceapp.ui.registration

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.RegistrationFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_WHITESPACE_ERROR
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class RegistrationFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val registrationViewModel: RegistrationViewModel by viewModels()
    private lateinit var binding: RegistrationFragmentBinding
    @Inject
    lateinit var editor: SharedPreferences.Editor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = RegistrationFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            registrationViewmodel = registrationViewModel
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        subscribeUi()
    }

    private fun setupListeners() {
        binding.apply {
            backButton.setOnClickListener { findNavController().navigateUp() }
            uploadProfilePictureButton.setOnClickListener { openStorageAccessFramework() }
            registerButton.setOnClickListener { checkHasConnectionBeforeRegisterAccount() }
        }
    }

    private fun subscribeUi() {
        registrationViewModel.emailAddressValueStatus.observe(
            viewLifecycleOwner,
            { emailValueStatus ->
                when (emailValueStatus) {
                    VALUE_STATUS_INVALID -> {
                        binding.registrationEmailTextInputLayout.error =
                            getString(R.string.invalid_email_error_text)
                    }
                    else -> {
                        binding.registrationEmailTextInputLayout.isErrorEnabled = false
                        binding.registrationEmailTextInputLayout.error = null
                    }
                }
            }
        )

        registrationViewModel.usernameValueStatus.observe(
            viewLifecycleOwner,
            { usernameValueStatus ->
                when (usernameValueStatus) {
                    VALUE_STATUS_INVALID -> {
                        binding.registrationUsernameTextInputLayout.error =
                            getString(R.string.invalid_username_error_text)
                    }
                    VALUE_STATUS_WHITESPACE_ERROR -> {
                        binding.registrationUsernameTextInputLayout.error =
                            getString(R.string.invalid_whitespace_error_text)
                    }
                    else -> {
                        binding.registrationUsernameTextInputLayout.isErrorEnabled = false
                        binding.registrationUsernameTextInputLayout.error = null
                    }
                }
            }
        )

        registrationViewModel.passwordValueStatus.observe(
            viewLifecycleOwner,
            { passwordValueStatus ->
                when (passwordValueStatus) {
                    VALUE_STATUS_INVALID -> {
                        binding.registrationPasswordTextInputLayout.error =
                            getString(R.string.invalid_password_error_text)
                    }
                    VALUE_STATUS_WHITESPACE_ERROR -> {
                        binding.registrationPasswordTextInputLayout.error =
                            getString(R.string.invalid_whitespace_error_text)
                    }
                    else -> {
                        binding.registrationPasswordTextInputLayout.isErrorEnabled = false
                        binding.registrationPasswordTextInputLayout.error = null
                    }
                }
            }
        )

        registrationViewModel.confirmPasswordValueStatus.observe(
            viewLifecycleOwner,
            { confirmPasswordValueStatus ->
                if (confirmPasswordValueStatus == VALUE_STATUS_INVALID) {
                    binding.registrationConfirmPasswordTextInputLayout.error =
                        getString(R.string.invalid_confirm_password_error_text)
                } else {
                    binding.registrationConfirmPasswordTextInputLayout.isErrorEnabled = false
                    binding.registrationConfirmPasswordTextInputLayout.error = null
                }
            }
        )
    }

    private fun openStorageAccessFramework() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, Constants.STORAGE_ACCESS_FRAMEWORK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == Constants.STORAGE_ACCESS_FRAMEWORK_CODE && resultCode == Activity.RESULT_OK && resultData != null) {
            val uri = resultData.data!!
            registrationViewModel.setProfilePictureUri(uri)

            Glide.with(requireContext())
                .load(uri)
                .circleCrop()
                .placeholder(R.drawable.ic_baseline_account_circle_grey_26dp)
                .into(binding.profilePictureImage)
        }
    }

    private fun checkHasConnectionBeforeRegisterAccount() {
        lifecycleScope.launch {
            val hasConnection = withContext(Dispatchers.IO) {
                checkConnectionStatusToFirebase()
            }
            if (hasConnection) {
                performAccountRegistration()
            } else {
                showShortSnackbar(getString(R.string.default_network_error))
            }
        }
    }

    private suspend fun performAccountRegistration() {
        if (registrationViewModel.isFormValid.value as Boolean) {
            disableUIInteractionAndHideKeyboard()
            registrationViewModel.enableLoadingProgressBar.value = true

            val result = withContext(Dispatchers.IO) { registrationViewModel.registerAccount() }
            when (result) {
                is Result.Success -> {
                    updateAccountUsernameInFirebase()
                }
                is Result.Error -> {
                    // Account created but sign in failed
                    val error = result.error
                    if (error == getString(R.string.account_created_failed_update_account_details_error)) {
                        findNavController().navigateUp()
                        showCancellableSnackbar(error)
                    } else {
                        showShortSnackbar(error)
                    }
                    registrationViewModel.enableLoadingProgressBar.value = false
                    enableUIInteraction()
                }
            }
        }
    }

    private suspend fun updateAccountUsernameInFirebase() {
        val isSuccessful = withContext(Dispatchers.IO) {
            registrationViewModel.updateAccountUsername()
        }
        if (!isSuccessful) {
            deleteFirebaseAccountUponFailedToUpdateAccountInfo()
            showShortSnackbar("Failed to create account. Update username error.")
        } else {
            mainViewModel.refreshUser(this.javaClass.simpleName)
            updateAccountProfilePictureInFirebase()
        }
    }

    private suspend fun updateAccountProfilePictureInFirebase() {
        val isSuccessful = withContext(Dispatchers.IO) {
            registrationViewModel.updateAccountProfilePicture()
        }
        if (!isSuccessful) {
            showLongToast("Account Created but failed to update account profile picture. Please retry again")
        }
        onAccountCreatedAndSignedIn()
    }

    private fun deleteFirebaseAccountUponFailedToUpdateAccountInfo() {
        lifecycleScope.launch {
            val isSuccessful = withContext(Dispatchers.IO) {
                deleteFirebaseAccount()
            }
            // Unsuccessful due to network error (e.g. poor or no connection)
            if (!isSuccessful) {
                showCancellableSnackbar(getString(R.string.account_created_failed_update_account_details_error))
                findNavController().navigateUp()
            }
            registrationViewModel.enableLoadingProgressBar.value = false
            enableUIInteraction()
        }
    }

    private fun onAccountCreatedAndSignedIn() {
        editor.putBoolean(getString(R.string.is_user_already_login), true).commit()
        showShortToast(getString(R.string.registration_successful_text))
        findNavController().navigate(R.id.action_registrationFragment_to_profileFragment)
        enableUIInteraction()
    }
}
