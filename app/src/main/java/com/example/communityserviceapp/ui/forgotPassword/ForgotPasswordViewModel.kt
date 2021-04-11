package com.example.communityserviceapp.ui.forgotPassword

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_EMPTY_DEFAULT
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_VALID
import com.example.communityserviceapp.util.Result
import com.example.communityserviceapp.util.sendResetPasswordEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val emailAddress = savedStateHandle.getLiveData(FORGOT_PASSWORD_EMAIL, "")
    private val _emailAddressValueStatus =
        savedStateHandle.getLiveData(FORGOT_PASSWORD_EMAIL_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val emailAddressValueStatus: LiveData<Int> = _emailAddressValueStatus
    val enableLoadingProgressBar = savedStateHandle.getLiveData(FORGOT_PASSWORD_LOADING_PROGRESS_BAR, false)
    val isFormValid = MediatorLiveData<Boolean>()

    init {
        isFormValid.addSource(emailAddress) { isFormValid.setValue(isEmailAddressValid()) }
    }

    companion object {
        const val FORGOT_PASSWORD_EMAIL = "forgot_password_email"
        const val FORGOT_PASSWORD_EMAIL_VALUE_STATUS = "forgot_password_email_value_status"
        const val FORGOT_PASSWORD_LOADING_PROGRESS_BAR = "forgot_password_loading_progress_bar"
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

    suspend fun resetAccountPassword(): Result<Any> {
        return sendResetPasswordEmail(emailAddress.value!!)
    }

    fun setEnableResetPasswordProgressBar(enableResetPasswordProgressBar: Boolean) {
        this.enableLoadingProgressBar.value = enableResetPasswordProgressBar
    }

    fun clearEmailAddress() {
        emailAddress.value = ""
    }
}
