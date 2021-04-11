package com.example.communityserviceapp.ui.profile.changeProfileDetails

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.UpdateEmailAddressBottomSheetBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.bottomSheet.*
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateEmailAddressBottomSheet : BaseBottomSheet() {

    private lateinit var binding: UpdateEmailAddressBottomSheetBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private var isDoneButtonClicked = false
    var newEmailAddress = MutableLiveData("")
    var currentPassword = MutableLiveData("")
    val enableLoadingProgressBar = MutableLiveData(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = UpdateEmailAddressBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            updateEmailAddressBottomSheet = this@UpdateEmailAddressBottomSheet
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
            cancelButton.setOnClickListener { dismissBottomSheet(Result.Error()) }

            doneButton.setOnClickListener {
                hideKeyboard()
                currentPasswordTextInputEditText.clearFocus()
                newEmailAddressTextInputEditText.clearFocus()

                when {
                    currentFirebaseUser == null -> {
                        dismissBottomSheet(Result.Error("Failed - User sensitive detail changed! Login and retry"))
                    }
                    currentFirebaseUser?.email == newEmailAddress.value -> {
                        showCustomErrorSnackbar(
                            binding.nestedScrollView, binding.doneButton,
                            getString(R.string.error_new_email_same_as_current_email)
                        )
                    }
                    else -> {
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
    }

    private fun subscribeUI() {
        newEmailAddress.observe(viewLifecycleOwner, { isNewEmailAddressValid() })
        currentPassword.observe(viewLifecycleOwner, { isCurrentPasswordValid() })
    }

    private suspend fun performFormValidationCheck() {
        isDoneButtonClicked = true
        if (isNewEmailAddressValid() && isCurrentPasswordValid()) {
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
                updateAccountEmail()
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

    private suspend fun updateAccountEmail() {
        val result = withContext(Dispatchers.IO) {
            updateFirebaseAccountEmail(newEmailAddress.value!!)
        }
        when (result) {
            is Result.Success -> {
                dismissBottomSheet(Result.Success(getString(R.string.update_email_address_successful_text)))
            }
            is Result.Error -> {
                showCustomErrorSnackbar(binding.nestedScrollView, binding.doneButton, result.error)
            }
        }
        enableLoadingProgressBar.value = false
        enableDialogAndUIInteraction()
    }

    private fun isNewEmailAddressValid(): Boolean {
        binding.apply {
            val newEmailAddressValue = newEmailAddress.value!!
            return if (newEmailAddressValue.isEmpty()) {
                if (isDoneButtonClicked) {
                    newEmailAddressTextInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
                    newEmailAddressTextInputLayout.error = "New email address is empty"
                } else {
                    newEmailAddressTextInputLayout.error = null
                    newEmailAddressTextInputLayout.isErrorEnabled = false
                }
                doneButton.isEnabled = false
                false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(newEmailAddressValue).matches()) {
                doneButton.isEnabled = false
                newEmailAddressTextInputLayout.endIconMode = TextInputLayout.END_ICON_NONE
                newEmailAddressTextInputLayout.error = "Invalid new email address"
                false
            } else {
                doneButton.isEnabled = true
                newEmailAddressTextInputLayout.error = null
                newEmailAddressTextInputLayout.isErrorEnabled = false
                newEmailAddressTextInputLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                true
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
