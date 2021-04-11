package com.example.communityserviceapp.ui.profile.changeProfileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.UpdatePasswordBottomSheetBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.bottomSheet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdatePasswordBottomSheet : BaseBottomSheet() {

    private lateinit var binding: UpdatePasswordBottomSheetBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private var isDoneButtonClicked = false
    var newPassword = MutableLiveData("")
    var currentPassword = MutableLiveData("")
    val enableLoadingProgressBar = MutableLiveData(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = UpdatePasswordBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            updatePasswordBottomSheet = this@UpdatePasswordBottomSheet
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainViewModel.refreshUser(this.javaClass.simpleName)
        setupListeners()
        subscribeUI()
    }

    private fun setupListeners() {
        binding.apply {
            cancelButton.setOnClickListener { dismiss() }

            doneButton.setOnClickListener {
                hideKeyboard()
                currentPasswordTextInputEditText.clearFocus()
                newPasswordTextInputEditText.clearFocus()

                if (currentFirebaseUser == null) {
                    dismissBottomSheet(Result.Error("Failed - User sensitive detail changed! Login and retry"))
                } else {
                    lifecycleScope.launch {
                        val hasConnection = withContext(Dispatchers.IO) {
                            checkConnectionStatusToFirebase()
                        }
                        if (hasConnection) {
                            performFormValidationCheck()
                        } else {
                            showCustomErrorSnackbar(
                                binding.nestedScrollView,
                                binding.doneButton,
                                getString(R.string.default_network_error)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun subscribeUI() {
        newPassword.observe(viewLifecycleOwner, { isNewPasswordValid() })
        currentPassword.observe(viewLifecycleOwner, { isCurrentPasswordValid() })
    }

    private suspend fun performFormValidationCheck() {
        isDoneButtonClicked = true
        if (isNewPasswordValid() && isCurrentPasswordValid()) {
            isDoneButtonClicked = true
            enableLoadingProgressBar.value = true
            // disableDialogInteraction() prevent a bug where after clicking 'done' button,
            // user can click on the button behind the bottom sheet
            disableDialogAndUIInteraction()
            reauthenticateUserAccount()
            isDoneButtonClicked = false
        }
    }

    private suspend fun reauthenticateUserAccount() {
        val result = withContext(Dispatchers.IO) {
            reauthenticateFirebaseUserAccount(currentPassword.value!!)
        }
        when (result) {
            is Result.Success -> {
                updateAccountPassword()
            }
            is Result.Error -> {
                showCustomErrorSnackbar(
                    binding.nestedScrollView, binding.doneButton,
                    result.error
                )
                enableLoadingProgressBar.value = false
                enableDialogAndUIInteraction()
            }
        }
    }

    private suspend fun updateAccountPassword() {
        val result = withContext(Dispatchers.IO) {
            updateFirebaseAccountPassword(newPassword.value!!)
        }
        when (result) {
            is Result.Success -> {
                dismiss()
                showBottomNavAnchoredSnackbar(getString(R.string.update_password_successful_text))
            }
            is Result.Error -> {
                showCustomErrorSnackbar(binding.nestedScrollView, binding.doneButton, result.error)
            }
        }
        enableLoadingProgressBar.value = false
        enableDialogAndUIInteraction()
    }

    // TODO: Reduce Cognitive Complexity
    private fun isNewPasswordValid(): Boolean {
        binding.apply {
            var newPasswordValue = newPassword.value!!
            return if (newPasswordValue.isEmpty() or newPasswordValue.isBlank()) {
                if (isDoneButtonClicked) {
                    newPasswordTextInputLayout.error = "New password length is less than 6"
                } else {
                    newPasswordTextInputLayout.error = null
                    newPasswordTextInputLayout.isErrorEnabled = false
                }
                doneButton.isEnabled = false
                false
            } else {
                newPasswordValue = newPasswordValue.trim()
                // check for trailing and leading whitespace
                if (newPasswordValue != newPassword.value) {
                    doneButton.isEnabled = false
                    newPasswordTextInputLayout.error =
                        getString(R.string.invalid_whitespace_error_text)
                    false
                } else {
                    if (newPasswordValue.length < 6) {
                        doneButton.isEnabled = false
                        newPasswordTextInputLayout.error = "New password length is less than 6"
                        false
                    } else {
                        val currentPasswordValue = currentPassword.value
                        if (currentPasswordValue == newPasswordValue) {
                            doneButton.isEnabled = false
                            newPasswordTextInputLayout.error =
                                "New password is the same as current password"
                            false
                        } else {
                            doneButton.isEnabled = true
                            newPasswordTextInputLayout.error = null
                            newPasswordTextInputLayout.isErrorEnabled = false
                            true
                        }
                    }
                }
            }
        }
    }

    private fun isCurrentPasswordValid(): Boolean {
        binding.apply {
            val currentPasswordValue = currentPassword.value!!
            return when {
                currentPasswordValue.isEmpty() -> {
                    if (isDoneButtonClicked) {
                        currentPasswordTextInputLayout.error = "Current password is empty"
                    } else {
                        currentPasswordTextInputLayout.error = null
                        currentPasswordTextInputLayout.isErrorEnabled = false
                    }
                    doneButton.isEnabled = false
                    false
                }
                currentPasswordValue.length < 6 -> {
                    doneButton.isEnabled = false
                    currentPasswordTextInputLayout.error = "Current password length is less than 6"
                    false
                }
                else -> {
                    doneButton.isEnabled = true
                    currentPasswordTextInputLayout.error = null
                    currentPasswordTextInputLayout.isErrorEnabled = false
                    true
                }
            }
        }
    }
}
