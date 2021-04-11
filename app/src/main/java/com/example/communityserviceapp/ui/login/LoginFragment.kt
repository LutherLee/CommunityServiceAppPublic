package com.example.communityserviceapp.ui.login

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
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.LoginFragmentBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private val loginViewModel: LoginViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    @Inject lateinit var editor: SharedPreferences.Editor
    private lateinit var binding: LoginFragmentBinding

    companion object {
        const val RC_SIGN_IN = 1001
        const val GOOGLE_SIGN_IN_INVALID_ACCOUNT = 5
        const val GOOGLE_SIGN_IN_NETWORK_ERROR = 7
        const val GOOGLE_SIGN_IN_INTERNAL_ERROR = 8
        const val GOOGLE_SIGN_IN_ERROR = 13
        const val GOOGLE_SIGN_IN_INTERRUPTED = 14
        const val GOOGLE_SIGN_IN_TIMEOUT = 15
        const val GOOGLE_SIGN_IN_API_NOT_CONNECTED = 17
        const val GOOGLE_SIGN_IN_CONNECTION_SUSPENDED_DURING_CALL = 20
        const val GOOGLE_SIGN_IN_RECONNECTION_TIMED_OUT = 22
        const val GOOGLE_SIGN_IN_RECONNECTION_TIMED_OUT_DURING_UPDATE = 21
        const val GOOGLE_SIGN_IN_FAILED = 12500
        const val GOOGLE_SIGN_IN_CANCELLED = 12501
        const val GOOGLE_SIGN_IN_CURRENTLY_IN_PROGRESS = 12502
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = LoginFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            loginViewmodel = loginViewModel
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        subscribeUI()
    }

    private fun setupListeners() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        binding.forgotPasswordButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        }

        binding.registerButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registrationFragment)
        }

        binding.loginButton.setOnClickListener {
            lifecycleScope.launch { checkConnectionBeforeLogin() }
        }

        binding.loginWithGoogleButton.setOnClickListener {
            loginViewModel.enableGoogleLoginProgressBar.value = true
            disableUIInteraction()
            startActivityForResult(getGoogleSignInClient().signInIntent, RC_SIGN_IN)
        }
    }

    private fun subscribeUI() {
        loginViewModel.emailAddressValueStatus.observe(
            viewLifecycleOwner,
            { valueStatus: Int? ->
                if (valueStatus == VALUE_STATUS_INVALID) {
                    binding.loginEmailTextInputLayout.error =
                        getString(R.string.invalid_email_error_text)
                } else {
                    binding.loginEmailTextInputLayout.isErrorEnabled = false
                    binding.loginEmailTextInputLayout.error = null
                }
            }
        )

        loginViewModel.passwordValueStatus.observe(
            viewLifecycleOwner,
            { valueStatus ->
                if (valueStatus == VALUE_STATUS_INVALID) {
                    binding.loginPasswordTextInputLayout.error =
                        getString(R.string.invalid_password_error_text)
                } else {
                    binding.loginPasswordTextInputLayout.isErrorEnabled = false
                    binding.loginPasswordTextInputLayout.error = null
                }
            }
        )
    }

    private suspend fun checkConnectionBeforeLogin() {
        hideKeyboard()
        val hasConnection = withContext(Dispatchers.IO) {
            checkConnectionStatusToFirebase()
        }
        if (hasConnection && loginViewModel.isFormValid.value as Boolean) {
            disableUIInteraction()
            loginViewModel.enableLoginProgressBar.value = true
            loginWithEmailAndPassword()
        } else {
            showShortSnackbar(getString(R.string.default_network_error))
        }
    }

    private suspend fun loginWithEmailAndPassword() {
        val result = withContext(Dispatchers.IO) { loginViewModel.loginWithEmailAndPassword() }
        when (result) {
            is Result.Success -> {
                updateAccountUsernameInFirebaseIfEmpty()
            }
            is Result.Error -> {
                showShortSnackbar(result.error)
            }
        }
        enableUIInteraction()
        loginViewModel.enableLoginProgressBar.value = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val accountIDToken = task.getResult(ApiException::class.java).idToken!!
                performFirebaseAuthentication(accountIDToken)
            } catch (error: ApiException) {
                // Google Sign In failed, update UI appropriately
                checkForGoogleSignInError(error.statusCode)
                enableUIInteraction()
                loginViewModel.enableGoogleLoginProgressBar.value = false
            }
        }
    }

    private fun performFirebaseAuthentication(accountIDToken: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                firebaseAuthWithGoogle(accountIDToken)
            }
            when (result) {
                is Result.Success -> {
                    editor.putBoolean(getString(R.string.login_with_google_sign_in), true).commit()
                    onSuccessfulLogin()
                }
                is Result.Error -> {
                    showShortSnackbar(result.error)
                }
            }
            loginViewModel.enableGoogleLoginProgressBar.value = false
            enableUIInteraction()
        }
    }

    private suspend fun updateAccountUsernameInFirebaseIfEmpty() {
        currentFirebaseUser?.let {
            if (it.displayName.isNullOrEmpty()) {
                val isSuccessful = withContext(Dispatchers.IO) {
                    loginViewModel.updateAccountUsername(it.uid)
                }
                if (!isSuccessful) {
                    showShortSnackbar(getString(R.string.default_network_error))
                } else {
                    onSuccessfulLogin()
                }
            } else {
                onSuccessfulLogin()
            }
        }
    }

    private fun onSuccessfulLogin() {
        editor.putBoolean(getString(R.string.is_user_already_login), true).commit()
        mainViewModel.refreshUser(this.javaClass.simpleName)
        mainViewModel.attachFirebaseRealTimeListenerThatDependsOnUserID()
        showShortToast("Welcome back ${currentFirebaseUser?.displayName}")
        findNavController().navigateUp()
    }

    /**
     * Handle specific google sign in error code with custom snackbar message
     * See: https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes
     */
    private fun checkForGoogleSignInError(errorCode: Int) {
        when (errorCode) {
            GOOGLE_SIGN_IN_CANCELLED -> {
                // Do nothing as this is initiated by the user on back press
            }
            GOOGLE_SIGN_IN_NETWORK_ERROR ->
                showShortSnackbar(getString(R.string.default_network_error))
            GOOGLE_SIGN_IN_FAILED ->
                showShortSnackbar(getString(R.string.google_sign_in_failed_error))
            GOOGLE_SIGN_IN_INTERNAL_ERROR ->
                showShortSnackbar(getString(R.string.google_sign_in_internal_error))
            GOOGLE_SIGN_IN_INVALID_ACCOUNT ->
                showShortSnackbar(getString(R.string.google_sign_in_invalid_account_error))
            GOOGLE_SIGN_IN_API_NOT_CONNECTED ->
                showShortSnackbar(getString(R.string.google_sign_in_api_not_connected_error))
            GOOGLE_SIGN_IN_CURRENTLY_IN_PROGRESS ->
                showShortSnackbar(getString(R.string.google_sign_in_currently_in_progress_error))
            GOOGLE_SIGN_IN_ERROR ->
                showShortSnackbar(getString(R.string.google_sign_in_error))
            GOOGLE_SIGN_IN_INTERRUPTED ->
                showShortSnackbar(getString(R.string.google_sign_in_interrupted_error))
            GOOGLE_SIGN_IN_RECONNECTION_TIMED_OUT ->
                showShortSnackbar(getString(R.string.google_sign_in_reconnection_timed_out_error))
            GOOGLE_SIGN_IN_CONNECTION_SUSPENDED_DURING_CALL ->
                showShortSnackbar(getString(R.string.google_sign_in_connection_suspended_during_call_error))
            GOOGLE_SIGN_IN_RECONNECTION_TIMED_OUT_DURING_UPDATE ->
                showShortSnackbar(getString(R.string.google_sign_in_reconnection_timed_out_during_update_error))
            GOOGLE_SIGN_IN_TIMEOUT ->
                showShortSnackbar(getString(R.string.google_sign_in_timeout_error))
            else ->
                showShortSnackbar("Unknown Error. Please try again.")
        }
    }
}
