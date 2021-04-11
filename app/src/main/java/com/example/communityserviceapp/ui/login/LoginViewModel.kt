package com.example.communityserviceapp.ui.login

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_EMPTY_DEFAULT
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_VALID
import com.example.communityserviceapp.util.Result
import com.example.communityserviceapp.util.signInWithEmailAndPassword
import com.example.communityserviceapp.util.updateFirebaseAccountUsername
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val emailAddress = savedStateHandle.getLiveData(LOGIN_EMAIL, "")
    val password = savedStateHandle.getLiveData(LOGIN_PASSWORD, "")
    private val _emailAddressValueStatus = savedStateHandle.getLiveData(LOGIN_PASSWORD_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val emailAddressValueStatus: LiveData<Int> = _emailAddressValueStatus
    private val _passwordValueStatus = savedStateHandle.getLiveData(LOGIN_EMAIL_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val passwordValueStatus: LiveData<Int> = _passwordValueStatus
    val enableLoginProgressBar = savedStateHandle.getLiveData(LOGIN_LOGIN_PROGRESS_BAR, false)
    val enableGoogleLoginProgressBar = savedStateHandle.getLiveData(LOGIN_GOOGLE_LOGIN_PROGRESS_BAR, false)
    val isFormValid = MediatorLiveData<Boolean>()

    init {
        isFormValid.addSource(emailAddress) { isFormValid.setValue(isEmailAddressValid() && isPasswordValid()) }
        isFormValid.addSource(password) { isFormValid.setValue(isEmailAddressValid() && isPasswordValid()) }
    }

    companion object {
        const val LOGIN_EMAIL = "login_email"
        const val LOGIN_PASSWORD = "login_password"
        const val LOGIN_EMAIL_VALUE_STATUS = "login_email_value_status"
        const val LOGIN_PASSWORD_VALUE_STATUS = "login_password_value_status"
        const val LOGIN_LOGIN_PROGRESS_BAR = "login_login_progress_bar"
        const val LOGIN_GOOGLE_LOGIN_PROGRESS_BAR = "login_google_login_progress_bar"
    }

    private fun isEmailAddressValid(): Boolean {
        val currentEmailAddress = emailAddress.value
        return when {
            currentEmailAddress == "" -> {
                _emailAddressValueStatus.value = VALUE_STATUS_EMPTY_DEFAULT
                false
            }
            Patterns.EMAIL_ADDRESS.matcher(currentEmailAddress).matches() -> {
                _emailAddressValueStatus.value = VALUE_STATUS_VALID
                true
            }
            else -> {
                _emailAddressValueStatus.value = VALUE_STATUS_INVALID
                false
            }
        }
    }

    private fun isPasswordValid(): Boolean {
        val currentPassword = password.value
        return when {
            currentPassword == "" -> {
                _passwordValueStatus.value = VALUE_STATUS_EMPTY_DEFAULT
                false
            }
            currentPassword!!.length >= 6 -> {
                _passwordValueStatus.value = VALUE_STATUS_VALID
                true
            }
            else -> {
                _passwordValueStatus.value = VALUE_STATUS_INVALID
                false
            }
        }
    }

    suspend fun loginWithEmailAndPassword(): Result<Any> {
        return signInWithEmailAndPassword(emailAddress.value!!, password.value!!)
    }

    suspend fun updateAccountUsername(currentUserID: String): Boolean {
        return updateFirebaseAccountUsername(currentUserID)
    }
}
