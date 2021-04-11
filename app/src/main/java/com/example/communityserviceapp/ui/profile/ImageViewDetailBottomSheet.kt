package com.example.communityserviceapp.ui.profile

import android.app.Dialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.example.communityserviceapp.R
import com.example.communityserviceapp.databinding.ImageViewDetailBottomSheetBinding
import com.example.communityserviceapp.util.bottomSheet.expandBottomSheetToScreenHeight
import com.example.communityserviceapp.util.currentFirebaseUser
import com.example.communityserviceapp.util.getCircularProgressDrawable
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.igreenwood.loupe.extensions.createLoupe
import com.igreenwood.loupe.extensions.setOnViewTranslateListener

class ImageViewDetailBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: ImageViewDetailBottomSheetBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            setOnShowListener {
                val bottomSheetDialog = it as BottomSheetDialog
                expandBottomSheetToScreenHeight(bottomSheetDialog)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            dialog?.window?.setWindowAnimations(-1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ImageViewDetailBottomSheetBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.statusBarColor = ContextCompat.getColor(
            requireContext(),
            R.color.black
        )
        loadProfilePicture()
        setupListener()
    }

    private fun loadProfilePicture() {
        Glide.with(requireContext())
            .load(currentFirebaseUser?.photoUrl)
            .apply(RequestOptions().dontTransform())
            .placeholder(getCircularProgressDrawable(requireContext()))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    createLoupe()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    createLoupe()
                    return false
                }
            })
            .error(R.drawable.custom_account_circle)
            .into(binding.loggedInProfilePictureImage)
    }

    private fun createLoupe() {
        createLoupe(
            binding.loggedInProfilePictureImage,
            binding.container
        ) {
            useFlingToDismissGesture = false
            setOnViewTranslateListener(
                onStart = { },
                onRestore = { },
                onDismiss = { findNavController().navigateUp() }
            )
        }
    }

    private fun setupListener() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
    }
}
