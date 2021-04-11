package com.example.communityserviceapp.ui.profile.changeProfileDetails

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.UploadPictureOptionBottomSheetBinding
import com.example.communityserviceapp.ui.MainViewModel
import com.example.communityserviceapp.util.*
import com.example.communityserviceapp.util.bottomSheet.dismissBottomSheet
import com.example.communityserviceapp.util.bottomSheet.expandBottomSheetToViewHeight
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class UploadPictureOptionBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: UploadPictureOptionBottomSheetBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private var currentPhotoPath: String? = null
    private var isSelectingImageToUpload = false

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DefaultBottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener { dialogInterface ->
                val bottomSheetDialog = dialogInterface as BottomSheetDialog
                expandBottomSheetToViewHeight(bottomSheetDialog)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = UploadPictureOptionBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainViewModel.refreshUser(this.javaClass.simpleName)
        setupListener()
    }

    override fun onStop() {
        super.onStop()
        if (!isSelectingImageToUpload && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // App process moved to background, disable anim
            dialog?.window?.setWindowAnimations(-1)
        }
    }

    override fun onDestroyView() {
        deleteCachedCameraTakenPhoto()
        super.onDestroyView()
    }

    private fun setupListener() {
        binding.apply {
            closeButton.setOnClickListener { dismissBottomSheet(Result.Error()) }
            openStorageButton.setOnClickListener { openStorageAccessFramework() }
            takePictureButton.setOnClickListener { dispatchTakePictureIntent() }
        }
    }

    private fun openStorageAccessFramework() {
        isSelectingImageToUpload = true
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, Constants.STORAGE_ACCESS_FRAMEWORK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        lifecycleScope.launch {
            val hasConnection = withContext(Dispatchers.IO) {
                checkConnectionStatusToFirebase()
            }
            if (requestCode == Constants.STORAGE_ACCESS_FRAMEWORK_CODE && resultCode == Activity.RESULT_OK) {
                onSuccessfulImageLoadFromStorageFramework(data, hasConnection)
            } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
                onSuccessfulImageCapture(hasConnection)
            }
            isSelectingImageToUpload = false
        }
    }

    private suspend fun onSuccessfulImageLoadFromStorageFramework(
        data: Intent?,
        hasConnection: Boolean
    ) {
        if (currentFirebaseUser == null) {
            dismissBottomSheet(Result.Error("Failed - User sensitive detail changed! Login and retry"))
        }
        data?.let {
            if (hasConnection) {
                val photoUri = it.data!!
                val imageBitmap = getImageBitmapFromPhotoUri(photoUri)
                prepareToUploadProfilePicture(imageBitmap)
            } else {
                dismissBottomSheet(Result.Error(getString(R.string.default_network_error)))
            }
        }
    }

    private suspend fun onSuccessfulImageCapture(hasConnection: Boolean) {
        if (currentFirebaseUser == null) {
            dismissBottomSheet(Result.Error("Failed - User sensitive detail changed! Login and retry"))
        }
        currentPhotoPath?.let {
            if (hasConnection) {
                val photoUri = Uri.fromFile(File(it))
                val imageBitmap = getImageBitmapFromPhotoUri(photoUri)
                val imageToBeUploaded =
                    checkIfPictureNeedToBeRotated(currentPhotoPath!!, imageBitmap)
                prepareToUploadProfilePicture(imageToBeUploaded)
            } else {
                dismissBottomSheet(Result.Error(getString(R.string.default_network_error)))
            }
        }
    }

    private suspend fun getImageBitmapFromPhotoUri(photoUri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT < 28) {
            withContext(Dispatchers.IO) {
                MediaStore.Images.Media.getBitmap(
                    requireContext().contentResolver,
                    photoUri
                )
            }
        } else {
            val source = ImageDecoder.createSource(requireContext().contentResolver, photoUri)
            withContext(Dispatchers.IO) { ImageDecoder.decodeBitmap(source) }
        }
    }

    private fun prepareToUploadProfilePicture(imageBitmap: Bitmap) {
        enableLoadingAnimation()

        val compressedBitmap = compressBitmapToByteArray(imageBitmap, 50)
        GlobalScope.launch {
            val isSuccessful = withContext(Dispatchers.IO) {
                uploadProfilePictureToCloudinary(compressedBitmap)
            }
            if (!isSuccessful) {
                dismissBottomSheet(
                    Result.Error(
                        "Failed to update profile picture. " +
                            "Please check internet connection and retry."
                    )
                )
            } else {
                dismissBottomSheet(Result.Success("Profile picture updated!"))
            }
        }
    }

    private fun deleteCachedCameraTakenPhoto() {
        GlobalScope.launch(Dispatchers.IO) {
            if (!currentPhotoPath.isNullOrBlank()) {
                Timber.d("Deleting cached camera taken photo")
                File(currentPhotoPath!!).delete()
            } else {
                Timber.d("Unable to delete cached camera taken photo")
            }
        }
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
                            GlobalScope.launch(Dispatchers.IO) {
                                val isSuccessful = updateFirebaseAccountProfilePicture(imageUri)
                                if (!isSuccessful) {
                                    val deleteToken = resultData["delete_token"] as String
                                    MediaManager.get().cloudinary.uploader().deleteByToken(
                                        deleteToken
                                    )
                                }
                                it.resume(isSuccessful)
                            }
                        }
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        it.resume(false)
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .startNow(requireContext())
        }

    private fun dispatchTakePictureIntent() {
        isSelectingImageToUpload = true
        val packageManager = requireContext().packageManager
        // Check if device has camera
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                // Ensure that there's a camera activity to handle the intent
                takePictureIntent.resolveActivity(packageManager)?.also {
                    // Create the File where the photo should go
                    val photoFile = try {
                        createImageFile()
                    } catch (ex: IOException) {
                        null
                    }
                    // Continue only if the File was successfully created
                    photoFile?.also {
                        val photoURI: Uri = FileProvider.getUriForFile(
                            requireContext(),
                            "com.example.communityserviceapp",
                            it
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                    }
                }
            }
        } else {
            dismissBottomSheet(Result.Error("Unable to proceed as device has no camera"))
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "PROFILE_PICTURE_CAMERA_UPLOAD",
            ".jpg",
            storageDir
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun compressBitmapToByteArray(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Using camera intent may rotate the picture on some device, this is a workaround
     * @link https://stackoverflow.com/questions/14066038/why-does-an-image-captured-using-camera-intent-gets-rotated-on-some-devices-on-a
     */
    private fun checkIfPictureNeedToBeRotated(photoPath: String, imageBitmap: Bitmap): Bitmap {
        val orientation = ExifInterface(photoPath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotateImage(imageBitmap, 90F)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotateImage(imageBitmap, 180F)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(imageBitmap, 270F)
            }
            else -> {
                imageBitmap
            }
        }
    }

    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    private fun enableLoadingAnimation() {
        binding.apply {
            contentContainer.visibility = View.GONE
            loadingContainer.visibility = View.VISIBLE
            loadingAnimationView.resumeAnimation()
        }
    }
}
