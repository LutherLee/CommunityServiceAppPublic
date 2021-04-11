package com.example.communityserviceapp.ui.registration

import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Patterns
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.communityserviceapp.ui.MyApplication.Companion.appContext
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_EMPTY_DEFAULT
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_INVALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_VALID
import com.example.communityserviceapp.util.Constants.VALUE_STATUS_WHITESPACE_ERROR
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val REGISTRATION_EMAIL = "registration_email"
        const val REGISTRATION_USERNAME = "registration_username"
        const val REGISTRATION_PASSWORD = "registration_password"
        const val REGISTRATION_CONFIRM_PASSWORD = "registration_confirm_password"
        const val REGISTRATION_EMAIL_VALUE_STATUS = "registration_email_value_status"
        const val REGISTRATION_USERNAME_VALUE_STATUS = "registration_username_value_status"
        const val REGISTRATION_PASSWORD_VALUE_STATUS = "registration_password_value_status"
        const val REGISTRATION_CONFIRM_PASSWORD_VALUE_STATUS =
            "registration_confirm_password_value_status"
        const val REGISTRATION_LOADING_PROGRESS_BAR = "registration_loading_progress_bar"
        const val REGISTRATION_PROFILE_PICTURE_URI = "registration_profile_picture_uri"
    }

    val emailAddress = savedStateHandle.getLiveData(REGISTRATION_EMAIL, "")
    val username = savedStateHandle.getLiveData(REGISTRATION_USERNAME, "")
    val password = savedStateHandle.getLiveData(REGISTRATION_PASSWORD, "")
    val confirmPassword = savedStateHandle.getLiveData(REGISTRATION_CONFIRM_PASSWORD, "")
    val emailAddressValueStatus =
        savedStateHandle.getLiveData(REGISTRATION_EMAIL_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val usernameValueStatus =
        savedStateHandle.getLiveData(REGISTRATION_USERNAME_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val passwordValueStatus =
        savedStateHandle.getLiveData(REGISTRATION_PASSWORD_VALUE_STATUS, VALUE_STATUS_EMPTY_DEFAULT)
    val confirmPasswordValueStatus = savedStateHandle.getLiveData(
        REGISTRATION_CONFIRM_PASSWORD_VALUE_STATUS,
        VALUE_STATUS_EMPTY_DEFAULT
    )
    val enableLoadingProgressBar = savedStateHandle.getLiveData(
        REGISTRATION_LOADING_PROGRESS_BAR,
        false
    )
    private val profilePictureUri = savedStateHandle.getLiveData<Uri>(
        REGISTRATION_PROFILE_PICTURE_URI,
        null
    )
    val isFormValid = MediatorLiveData<Boolean>()

    init {
        isFormValid.addSource(emailAddress) {
            isFormValid.setValue(isEmailAddressValid() && isUsernameValid() && isPasswordValid() && isConfirmPasswordValid())
        }

        isFormValid.addSource(username) {
            isFormValid.setValue(isEmailAddressValid() && isUsernameValid() && isPasswordValid() && isConfirmPasswordValid())
        }

        isFormValid.addSource(password) {
            isFormValid.setValue(isEmailAddressValid() && isUsernameValid() && isPasswordValid() && isConfirmPasswordValid())
        }

        isFormValid.addSource(confirmPassword) {
            isFormValid.setValue(isEmailAddressValid() && isUsernameValid() && isPasswordValid() && isConfirmPasswordValid())
        }
    }

    private fun isEmailAddressValid(): Boolean {
        val currentEmailAddress = emailAddress.value
        return when {
            currentEmailAddress == "" -> {
                emailAddressValueStatus.value = VALUE_STATUS_EMPTY_DEFAULT
                false
            }
            Patterns.EMAIL_ADDRESS.matcher(currentEmailAddress).matches() -> {
                emailAddressValueStatus.value = VALUE_STATUS_VALID
                true
            }
            else -> {
                emailAddressValueStatus.value = VALUE_STATUS_INVALID
                false
            }
        }
    }

    // Remove leading and trailing whitespace, check length
    // check for trailing and leading whitespace
    private fun isUsernameValid(): Boolean {
        var currentUsername = username.value
        return if (currentUsername == "") {
            usernameValueStatus.value = VALUE_STATUS_EMPTY_DEFAULT
            false
        } else {
            currentUsername =
                currentUsername!!.trim { it <= ' ' } // Remove leading and trailing whitespace, check length
            // check for trailing and leading whitespace
            if (currentUsername != username.value) {
                usernameValueStatus.value = VALUE_STATUS_WHITESPACE_ERROR
                false
            } else {
                if (currentUsername.length <= 20) {
                    usernameValueStatus.value = VALUE_STATUS_VALID
                    true
                } else {
                    usernameValueStatus.value = VALUE_STATUS_INVALID
                    false
                }
            }
        }
    }

    // Remove leading and trailing whitespace, check length
    // check for trailing and leading whitespace
    private fun isPasswordValid(): Boolean {
        var currentPassword = password.value
        return if (currentPassword == "") {
            passwordValueStatus.value = VALUE_STATUS_EMPTY_DEFAULT
            false
        } else {
            currentPassword =
                currentPassword!!.trim { it <= ' ' } // Remove leading and trailing whitespace, check length
            // check for trailing and leading whitespace
            if (currentPassword != password.value) {
                passwordValueStatus.value = VALUE_STATUS_WHITESPACE_ERROR
                false
            } else {
                if (currentPassword.length >= 6) {
                    passwordValueStatus.value = VALUE_STATUS_VALID
                    true
                } else {
                    passwordValueStatus.value = VALUE_STATUS_INVALID
                    false
                }
            }
        }
    }

    private fun isConfirmPasswordValid(): Boolean {
        val currentConfirmPassword = confirmPassword.value
        return if (currentConfirmPassword == "") {
            confirmPasswordValueStatus.value = VALUE_STATUS_EMPTY_DEFAULT
            false
        } else {
            val currentPassword = password.value
            if (currentConfirmPassword == currentPassword) {
                confirmPasswordValueStatus.value = VALUE_STATUS_VALID
                true
            } else {
                confirmPasswordValueStatus.value = VALUE_STATUS_INVALID
                false
            }
        }
    }

    suspend fun registerAccount(): Result<Any> {
        return createUserWithEmailAndPassword(emailAddress.value!!, password.value!!)
    }

    suspend fun updateAccountUsername() = updateFirebaseAccountUsername(username.value!!.trim())

    suspend fun updateAccountProfilePicture(): Boolean {
        val pictureUri = profilePictureUri.value
        return if (pictureUri != null) {
            val bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), pictureUri)
            val compressedBitmap = compressBitmapToByteArray(bitmap, 50)
            uploadProfilePictureToCloudinary(compressedBitmap)
        } else {
            true
        }
    }

    fun setProfilePictureUri(profilePictureUri: Uri) {
        this.profilePictureUri.value = profilePictureUri
    }

    private suspend fun uploadProfilePictureToCloudinary(compressedBitmap: ByteArray) =
        suspendCoroutine<Boolean> {
            MediaManager.get().upload(compressedBitmap)
                .unsigned("squzagea")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(
                        requestId: String?,
                        resultData: MutableMap<Any?, Any?>?
                    ) {
                        if (resultData != null && resultData.isNotEmpty()) {
                            val imageUri = Uri.parse(resultData["url"] as String)
                            viewModelScope.launch(Dispatchers.IO) {
                                val isSuccessful = updateFirebaseAccountProfilePicture(imageUri)
                                if (!isSuccessful) {
                                    val deleteToken = resultData["delete_token"] as String
                                    MediaManager.get().cloudinary.uploader()
                                        .deleteByToken(deleteToken)
                                }
                                it.resume(isSuccessful)
                            }
                        }
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        Timber.d("Error Uploading Profile Picture")
                        it.resume(false)
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .startNow(appContext)
        }

    private fun compressBitmapToByteArray(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}
