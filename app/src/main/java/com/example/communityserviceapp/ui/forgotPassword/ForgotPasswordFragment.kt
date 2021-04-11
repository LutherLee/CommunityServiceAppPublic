package com.example.communityserviceapp.ui.forgotPassword

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.ForgotPasswordFragmentBinding
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ForgotPasswordFragment : Fragment() {

    private val forgotPasswordViewModel: ForgotPasswordViewModel by viewModels()
    private lateinit var binding: ForgotPasswordFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ForgotPasswordFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            forgotPasswordViewmodel = forgotPasswordViewModel
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        subscribeUI()
    }

    private fun setupListeners() {
        binding.apply {
            backButton.setOnClickListener { findNavController().navigateUp() }

            resetPasswordButton.setOnClickListener {
                if (forgotPasswordViewModel.isFormValid.value as Boolean) {
                    disableUIInteraction()
                    hideKeyboard()
                    forgotPasswordViewModel.enableLoadingProgressBar.value = true
                    resetAccountPassword()
                }
            }
        }
    }

    private fun resetAccountPassword() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                forgotPasswordViewModel.resetAccountPassword()
            }
            when (result) {
                is Result.Success -> {
                    showLongToast(getString(R.string.password_reset_email_sent_message))
                    findNavController().navigateUp()
                }
                is Result.Error -> {
                    showShortSnackbar(result.error)
                }
            }
            enableUIInteraction()
            forgotPasswordViewModel.setEnableResetPasswordProgressBar(false)
        }
    }

    private fun subscribeUI() {
        forgotPasswordViewModel.emailAddressValueStatus.observe(
            viewLifecycleOwner,
            { valueStatus: Int ->
                if (valueStatus == VALUE_STATUS_INVALID) {
                    binding.forgotPasswordEmailTextInputLayout.error =
                        getString(R.string.invalid_email_error_text)
                } else {
                    binding.forgotPasswordEmailTextInputLayout.isErrorEnabled = false
                    binding.forgotPasswordEmailTextInputLayout.error = null
                    // Bug with this version of material design such that end icon of textInputLayout is still
                    // clickable even when not on focus. Workaround is to override end icon on click behavior
                    // Note: Since the listener is set every time input value change as long as value is
                    // not invalid, please find alternative if performance is affected
                    binding.forgotPasswordEmailTextInputLayout.endIconMode =
                        TextInputLayout.END_ICON_CLEAR_TEXT
                    binding.forgotPasswordEmailTextInputLayout.setEndIconOnClickListener {
                        if (binding.forgotPasswordEmailTextInputEditText.isFocused) {
                            forgotPasswordViewModel.clearEmailAddress()
                        }
                    }
                }
            }
        )
    }
}
