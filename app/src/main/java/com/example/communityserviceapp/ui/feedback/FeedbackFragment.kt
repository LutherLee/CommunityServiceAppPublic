package com.example.communityserviceapp.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.FeedbackFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class FeedbackFragment : Fragment() {

    private val feedbackViewModel: FeedbackViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FeedbackFragmentBinding
    private val args: FeedbackFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FeedbackFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            feedbackViewmodel = feedbackViewModel
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshUser(this.javaClass.simpleName)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActionBar()
        setupListeners()
        subscribeUI()
        checkIfShouldAutoFillEmailAndTitle()
    }

    override fun onDestroyView() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(null)
        super.onDestroyView()
    }

    private fun setupActionBar() {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)
        (requireActivity() as AppCompatActivity).supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupListeners() {
        binding.submitButton.setOnClickListener {
            if (feedbackViewModel.isFormValid.value as Boolean) {
                checkIfHasConnection()
            }
        }
    }

    private fun checkIfHasConnection() {
        lifecycleScope.launch {
            val hasConnection = withContext(Dispatchers.IO) {
                checkConnectionStatusToFirebase()
            }
            if (hasConnection) {
                disableUIInteraction()
                feedbackViewModel.enableLoadingProgressBar.value = true
                uploadSupportMessage()
            } else {
                showShortToast(getString(R.string.default_network_error))
            }
        }
    }

    private suspend fun uploadSupportMessage() {
        val isSuccessful = withContext(Dispatchers.IO) {
            feedbackViewModel.uploadFeedbackMessage()
        }
        if (isSuccessful) {
            showShortToast(getString(R.string.feedback_message_submit_success))
            findNavController().navigateUp()
        } else {
            showShortToast(getString(R.string.feedback_message_submit_error))
        }
        feedbackViewModel.enableLoadingProgressBar.value = false
        enableUIInteraction()
    }

    private fun subscribeUI() {
        feedbackViewModel.contactEmailValueStatus.observe(
            viewLifecycleOwner,
            {
                if (it == Constants.VALUE_STATUS_INVALID) {
                    binding.contactEmailTextInputLayout.error =
                        getString(R.string.invalid_email_error_text)
                } else {
                    binding.contactEmailTextInputLayout.isErrorEnabled = false
                    binding.contactEmailTextInputLayout.error = null
                }
            }
        )

        feedbackViewModel.titleValueStatus.observe(
            viewLifecycleOwner,
            {
                if (it == Constants.VALUE_STATUS_WHITESPACE_ERROR) {
                    binding.titleTextInputLayout.error =
                        getString(R.string.invalid_title_error_text)
                } else {
                    binding.titleTextInputLayout.isErrorEnabled = false
                    binding.titleTextInputLayout.error = null
                }
            }
        )

        feedbackViewModel.messageValueStatus.observe(
            viewLifecycleOwner,
            {
                if (it == Constants.VALUE_STATUS_WHITESPACE_ERROR) {
                    binding.messageTextInputLayout.error =
                        getString(R.string.invalid_message_error_text)
                } else {
                    binding.messageTextInputLayout.isErrorEnabled = false
                    binding.messageTextInputLayout.error = null
                }
            }
        )
    }

    private fun checkIfShouldAutoFillEmailAndTitle() {
        val email = args.email
        val title = args.title
        if (!email.isNullOrEmpty()) {
            feedbackViewModel.contactEmail.value = email
        }
        if (!title.isNullOrEmpty()) {
            feedbackViewModel.title.value = title
        }
    }
}
