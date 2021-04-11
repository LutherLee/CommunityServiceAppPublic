package com.example.communityserviceapp.util.bottomSheet

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.example.communityserviceapp.R
import com.example.communityserviceapp.util.ResizeHeightAnimation
import com.example.communityserviceapp.util.Result
import com.example.communityserviceapp.util.disableUIInteraction
import com.example.communityserviceapp.util.enableUIInteraction
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import java.lang.ref.WeakReference
import kotlin.math.ceil

private val resizeHeightAnimation = ResizeHeightAnimation()
private var isAnySnackbarShowing = false
var onBottomSheetDismissListener: ((Result<Any>) -> Unit)? = null
var onShowAllJobNatureCategoryBottomSheetDismissListener: ((Result<Any>) -> Unit)? = null

/**
 * This disable bottom sheet to dismiss on touch event outside the bottom sheet.
 * To be executed in setCancelable() method by overriding default implementation.
 */
fun makeBottomSheetUncancellable(dialog: Dialog, cancelable: Boolean) {
    val touchOutsideView = dialog.window!!.decorView.findViewById<View>(R.id.touch_outside)
    val bottomSheetView = dialog.window!!.decorView.findViewById<View>(R.id.design_bottom_sheet)
    if (cancelable) {
        touchOutsideView.setOnClickListener {
            if (dialog.isShowing) {
                dialog.cancel()
            }
        }
        BottomSheetBehavior.from(bottomSheetView).setHideable(true)
    } else {
        touchOutsideView.setOnClickListener(null)
        BottomSheetBehavior.from(bottomSheetView).setHideable(false)
    }
}

fun showCustomErrorSnackbar(view: View, doneButton: Button?, errorMessage: String) {
    if (!isAnySnackbarShowing) {
        doneButton?.isEnabled = false
        isAnySnackbarShowing = true
        val snackbar = createCustomSnackbar(view, doneButton, errorMessage).apply {
            view.elevation = 0f
            view.translationY =
                6 * (view.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
        }
        updateBottomSheetViewHeight(view, checkTheNumberOfTextLine(errorMessage))
        snackbar.show()
    }
}

private fun createCustomSnackbar(view: View, doneButton: Button?, errorMessage: String): Snackbar {
    return Snackbar.make(view, errorMessage, Snackbar.LENGTH_SHORT).apply {
        duration = 1700
        addCallback(object : Snackbar.Callback() {
            override fun onDismissed(snackbar: Snackbar, event: Int) {
                isAnySnackbarShowing = false
                doneButton?.isEnabled = true
            }
        })
    }
}

private fun updateBottomSheetViewHeight(view: View, numOfTextLine: Int) {
    val initialViewHeight = view.height
    resizeHeightAnimation.setView(WeakReference(view))

    // Restore view original height after 2000 milliseconds
    // TODO: Use anonymous inner class to prevent memory leak
    Handler().postDelayed(
        {
            try {
                showResizeAnimation(view, initialViewHeight)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        },
        2000
    )
    // Check screen orientation and rough estimation of numOfTextLine
    // snackbar will display then adjust screen size
    val orientation = view.resources.configuration.orientation
    if (numOfTextLine > 1) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            showResizeAnimation(view, initialViewHeight + 230)
        } else {
            showResizeAnimation(view, initialViewHeight + 160)
        }
    } else {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            showResizeAnimation(view, initialViewHeight + 150)
        } else {
            showResizeAnimation(view, initialViewHeight + 60)
        }
    }
}

private fun showResizeAnimation(view: View, finalHeight: Int) {
    resizeHeightAnimation.apply {
        setHeights(view.height, finalHeight)
        duration = 300
    }
    view.startAnimation(resizeHeightAnimation)
}

private fun checkTheNumberOfTextLine(message: String): Int {
    val bounds = Rect()
    val paint = Paint()
    paint.textSize = 14f // default text size
    paint.getTextBounds(message, 0, message.length, bounds)
    return ceil((bounds.width().toFloat() / 300).toDouble()).toInt() // Rough estimation
}

fun BottomSheetDialogFragment.expandBottomSheetToViewHeight(bottomSheetDialog: BottomSheetDialog) {
    val bottomSheet = bottomSheetDialog.findViewById<View>(R.id.design_bottom_sheet) as FrameLayout
    val behavior: BottomSheetBehavior<*> = BottomSheetBehavior.from(bottomSheet)
    behavior.state = BottomSheetBehavior.STATE_EXPANDED
    requireView().parent.requestLayout()
    behavior.peekHeight = requireView().height
}

/**
 * This also automatically expand bottom sheet to screen height without the default
 * bottom sheet behaviour (peek height, expanded, collapsed, etc.)
 */
fun disableBottomSheetDragDownToDismiss(bottomSheetDialog: BottomSheetDialog) {
    val bottomSheet = bottomSheetDialog.findViewById<View>(R.id.design_bottom_sheet)
    bottomSheet?.let { view ->
        setupFullHeight(view)
        val layoutParams = view.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.behavior = null
    }
}

fun BottomSheetDialogFragment.expandBottomSheetToScreenHeight(
    bottomSheetDialog: BottomSheetDialog,
    disableDragDownToDismiss: Boolean = false
) {
    val bottomSheet = bottomSheetDialog.findViewById<View>(R.id.design_bottom_sheet)
    bottomSheet?.let { view ->
        setupFullHeight(view)
        val behaviour = BottomSheetBehavior.from(view)
        behaviour.state = BottomSheetBehavior.STATE_EXPANDED
        behaviour.peekHeight = getWindowHeight(requireActivity())
        if (disableDragDownToDismiss) {
            behaviour.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                        behaviour.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }
    }
}

private fun setupFullHeight(bottomSheet: View) {
    val layoutParams = bottomSheet.layoutParams
    layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
    bottomSheet.layoutParams = layoutParams
}

private fun getWindowHeight(activity: Activity): Int {
    val displayMetrics = DisplayMetrics()
    activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
    return displayMetrics.heightPixels
}

fun disableDialogInteraction(dialog: Dialog) {
    dialog.window!!.setFlags(
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    )
    dialog.setCancelable(false)
}

fun enableDialogInteraction(dialog: Dialog) {
    dialog.window!!.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    dialog.setCancelable(true)
}

fun BottomSheetDialogFragment.enableDialogAndUIInteraction() {
    enableDialogInteraction(requireDialog())
    enableUIInteraction()
}

fun BottomSheetDialogFragment.disableDialogAndUIInteraction() {
    disableDialogInteraction(requireDialog())
    disableUIInteraction()
}

fun BottomSheetDialogFragment.dismissBottomSheet(result: Result<Any>) {
    dismiss()
    onBottomSheetDismissListener?.invoke(result)
}
