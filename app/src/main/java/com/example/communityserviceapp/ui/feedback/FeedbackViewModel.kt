package com.example.communityserviceapp.ui.feedback

import android.util.Patterns
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_EMPTY_DEFAULT
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_VALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_WHITESPACE_ERROR
import com.example.communityserviceapp.util.addFeedbackMessageToFirestore
import com.google.firebase.firestore.FieldValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.collections.set

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val contactEmail = savedStateHandle.getLiveData(SUPPORT_CONTACT_EMAIL, "")
    val title = savedStateHandle.getLiveData(SUPPORT_TITLE, "")
    val message = savedStateHandle.getLiveData(SUPPORT_MESSAGE, "")
    val contactEmailValueStatus = savedStateHandle.getLiveData(SUPPORT_CONTACT_EMAIL_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val titleValueStatus = savedStateHandle.getLiveData(SUPPORT_TITLE_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val messageValueStatus = savedStateHandle.getLiveData(SUPPORT_MESSAGE_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val enableLoadingProgressBar = savedStateHandle.getLiveData(SUPPORT_LOADING_PROGRESS_BAR, false)
    val isFormValid = MediatorLiveData<Boolean>()

    companion object {
        const val SUPPORT_CONTACT_EMAIL = "support_contact_email"
        const val SUPPORT_TITLE = "support_title"
        const val SUPPORT_MESSAGE = "support_message"
        const val SUPPORT_CONTACT_EMAIL_VALUE_STATUS = "support_contact_email_value_status"
        const val SUPPORT_TITLE_VALUE_STATUS = "support_title_value_status"
        const val SUPPORT_MESSAGE_VALUE_STATUS = "support_message_value_status"
        const val SUPPORT_LOADING_PROGRESS_BAR = "support_loading_progress_bar"
    }

    init {
        isFormValid.addSource(contactEmail) {
            isFormValid.setValue(
                isEmailAddressValid() &&
                    isTitleOrMessageValid(title, titleValueStatus) &&
                    isTitleOrMessageValid(message, messageValueStatus)
            )
        }
        isFormValid.addSource(title) {
            isFormValid.setValue(
                isEmailAddressValid() &&
                    isTitleOrMessageValid(title, titleValueStatus) &&
                    isTitleOrMessageValid(message, messageValueStatus)
            )
        }
        isFormValid.addSource(message) {
            isFormValid.setValue(
                isEmailAddressValid() &&
                    isTitleOrMessageValid(title, titleValueStatus) &&
                    isTitleOrMessageValid(message, messageValueStatus)
            )
        }
    }

    private fun isEmailAddressValid(): Boolean {
        val currentContactEmail = contactEmail.value
        return when {
            currentContactEmail == "" -> {
                contactEmailValueStatus.value = VALUE_STATUS_EMPTY_DEFAULT
                false
            }
            Patterns.EMAIL_ADDRESS.matcher(currentContactEmail).matches() -> {
                contactEmailValueStatus.value = VALUE_STATUS_VALID
                true
            }
            else -> {
                contactEmailValueStatus.value = VALUE_STATUS_INVALID
                false
            }
        }
    }

    private fun isTitleOrMessageValid(input: MutableLiveData<String>, inputStatus: MutableLiveData<Int>): Boolean {
        val value = input.value!!
        return if (value.isEmpty()) {
            inputStatus.value = VALUE_STATUS_EMPTY_DEFAULT
            false
        } else {
            // check if the value is only whitespace
            if (value.trim().isEmpty()) {
                inputStatus.value = VALUE_STATUS_WHITESPACE_ERROR
                false
            } else {
                inputStatus.value = VALUE_STATUS_VALID
                true
            }
        }
    }

    suspend fun uploadFeedbackMessage(): Boolean {
        val data = mutableMapOf<String, Any?>()
        data["sender"] = contactEmail.value
        data["title"] = title.value!!.trim()
        data["message"] = message.value!!.trim()
        data["timestamp:"] = FieldValue.serverTimestamp()
        return addFeedbackMessageToFirestore(data)
    }
}
