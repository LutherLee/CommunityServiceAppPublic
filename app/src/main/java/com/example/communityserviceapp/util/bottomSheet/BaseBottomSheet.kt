package com.example.communityserviceapp.util.bottomSheet

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import com.example.communityserviceapp.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Default bottom sheet style with rounded corner and expanded to view full height
 *
 * Note: Default behaviour may be overridden by subclasses' implementation
 */
abstract class BaseBottomSheet : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.RoundedBottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener { dialogInterface ->
                val bottomSheetDialog = dialogInterface as BottomSheetDialog
                expandBottomSheetToViewHeight(bottomSheetDialog)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dialog?.window?.setWindowAnimations(-1)
        }
    }
}
