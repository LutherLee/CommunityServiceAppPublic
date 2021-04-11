package com.example.communityserviceapp.ui.profile.changeProfileDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.ChangeUsernameBottomSheetBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.bottomSheet.*
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChangeUsernameBottomSheet : BaseBottomSheet() {

    private lateinit var binding: ChangeUsernameBottomSheetBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private var isDoneButtonClicked = false
    val enableLoadingProgressBar = MutableLiveData(false)
    val newUsername = MutableLiveData("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChangeUsernameBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            changeUsernameBottomSheet = this@ChangeUsernameBottomSheet
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
        binding.cancelButton.setOnClickListener { dismissBottomSheet(Result.Error()) }

        binding.doneButton.setOnClickListener {
            if (currentFirebaseUser == null) {
                dismissBottomSheet(Result.Error("Failed - User sensitive detail changed! Login and retry"))
            } else {
                isDoneButtonClicked = true
                if (isNewUsernameValid()) {
                    enableLoadingProgressBar.value = true
                    // disableDialogInteraction() prevent a bug where after clicking 'done' button,
                    // user can click on the button behind the bottom sheet
                    disableDialogAndUIInteraction()
                    checkIfShouldUpdateAccountUsername(newUsername.value!!)
                }
                isDoneButtonClicked = false
            }
        }
    }

    private fun subscribeUI() {
        newUsername.observe(viewLifecycleOwner, { isNewUsernameValid() })
    }

    private fun checkIfShouldUpdateAccountUsername(newUsername: String) {
        if (currentFirebaseUser?.displayName == newUsername) {
            enableLoadingProgressBar.value = false
            showCustomErrorSnackbar(
                binding.nestedScrollView, binding.doneButton,
                getString(R.string.error_new_username_same_as_current_username)
            )
            enableDialogAndUIInteraction()
        } else {
            lifecycleScope.launch {
                val isSuccessful = updateFirebaseAccountUsername(newUsername)
                if (!isSuccessful) {
                    showCustomErrorSnackbar(
                        binding.nestedScrollView, binding.doneButton,
                        getString(R.string.error_changing_account_username)
                    )
                } else {
                    dismissBottomSheet(Result.Success())
                    showBottomNavAnchoredSnackbar(getString(R.string.change_username_successful_text))
                }
                enableLoadingProgressBar.value = false
                enableDialogAndUIInteraction()
            }
        }
    }

    private fun isNewUsernameValid(): Boolean {
        binding.apply {
            var newUsernameValue = newUsername.value!!
            return if (newUsernameValue.isEmpty() or newUsernameValue.isBlank()) {
                if (isDoneButtonClicked) {
                    newUsernameTextInputLayout.error = "New username cannot be empty"
                }
                doneButton.isEnabled = false
                false
            } else {
                newUsernameValue = newUsernameValue.trim()
                // check for trailing and leading whitespace
                if (newUsernameValue != newUsername.value) {
                    doneButton.isEnabled = false
                    newUsernameTextInputLayout.error =
                        getString(R.string.invalid_whitespace_error_text)
                    false
                } else {
                    if (newUsernameValue.length > 20) {
                        doneButton.isEnabled = false
                        newUsernameTextInputLayout.error = "New username length cannot exceed 20"
                        false
                    } else {
                        doneButton.isEnabled = true
                        newUsernameTextInputLayout.error = null
                        newUsernameTextInputLayout.isErrorEnabled = false
                        newUsernameTextInputLayout.endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
                        true
                    }
                }
            }
        }
    }
}
