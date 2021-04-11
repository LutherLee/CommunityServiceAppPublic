package com.example.communityserviceapp.util

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.communityserviceapp.R
import com.example.communityserviceapp.ui.MainActivity
import com.example.communityserviceapp.ui.MyApplication
import com.example.communityserviceapp.ui.MyApplication.Companion.appContext
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_ACTION
import com.example.communityserviceapp.util.Constants.CHIP_TYPE_FILTER
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber
import java.lang.ref.WeakReference

fun getCircularProgressDrawable(mContext: Context? = null): CircularProgressDrawable {
    val context = mContext ?: appContext
    return WeakReference(CircularProgressDrawable(context)).apply {
        get()!!.strokeWidth = 7f
        get()!!.centerRadius = 30f
        get()!!.setColorSchemeColors(
            ContextCompat.getColor(
                context,
                R.color.colorPrimary
            )
        )
        get()!!.start()
    }.get()!!
}

fun Fragment.setupSwipeRefreshColorScheme(swipeRefreshLayout: SwipeRefreshLayout) {
    swipeRefreshLayout.setColorSchemeColors(
        ContextCompat.getColor(requireContext(), R.color.colorPrimary),
        ContextCompat.getColor(requireContext(), R.color.faintblue), Color.BLACK
    )
}

fun Fragment.setSoftInputModeToAdjustResize() {
    requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

inline fun enableCollapsingToolbarScroll(collapsingToolbar: CollapsingToolbarLayout, action: () -> Int) {
    val params = collapsingToolbar.layoutParams as AppBarLayout.LayoutParams
    params.scrollFlags = (action())
    collapsingToolbar.layoutParams = params
}

fun disableCollapsingToolbarScroll(collapsingToolbar: CollapsingToolbarLayout) {
    val params = collapsingToolbar.layoutParams as AppBarLayout.LayoutParams
    params.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
    collapsingToolbar.layoutParams = params
}

fun inflateChip(
    chipType: Int,
    value: String,
    context: Context,
    isClickable: Boolean = true,
    minTouchTargetSize: Boolean = false
): Chip {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    return when (chipType) {
        CHIP_TYPE_FILTER -> {
            inflater.inflate(R.layout.item_filter_chip, null, false) as Chip
        }
        CHIP_TYPE_ACTION -> {
            inflater.inflate(R.layout.item_action_chip, null, false) as Chip
        }
        else -> {
            inflater.inflate(R.layout.item_action_chip, null, false) as Chip
        }
    }.apply {
        setEnsureMinTouchTargetSize(minTouchTargetSize)
        text = value
        this.isClickable = isClickable
    }
}

fun Fragment.getColor(color: Int): Int = ContextCompat.getColor(requireContext(), color)

fun getColor(color: Int): Int = ContextCompat.getColor(appContext, color)

fun Fragment.showShortToast(message: String) =
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

fun Fragment.showLongToast(message: String) =
    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

fun showShortToast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

fun Fragment.showKeyboard() {
    val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
}

fun Fragment.hideKeyboard() = hideKeyboard(requireActivity(), requireView())

fun Fragment.hideBottomNavigationView() = getMainActivity().hideBottomNavigationView()

fun Fragment.disableUIInteraction() = getMainActivity().disableUIInteraction()

fun Fragment.enableUIInteraction() = getMainActivity().enableUIInteraction()

private fun Fragment.getMainActivity() = requireActivity() as MainActivity

fun Fragment.disableUIInteractionAndHideKeyboard() {
    disableUIInteraction()
    hideKeyboard(requireActivity(), requireView())
}

fun Fragment.showBottomNavAnchoredSnackbar(message: String, duration: Int = 1200) {
    val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav)
    Snackbar.make(bottomNav, message, Snackbar.LENGTH_SHORT).apply {
        anchorView = bottomNav
        this.duration = duration
        animationMode = Snackbar.ANIMATION_MODE_SLIDE
    }.show()
}

fun Fragment.showShortSnackbar(message: String) {
    Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).apply {
        animationMode = Snackbar.ANIMATION_MODE_SLIDE
    }.show()
}

fun Fragment.showCancellableSnackbar(message: String) {
    Snackbar.make(requireView(), message, Snackbar.LENGTH_INDEFINITE).apply {
        setAction("Close") {
            // Leave empty to dismiss
        }
        view.findViewById<TextView>(R.id.snackbar_text).maxLines = 4
    }.show()
}

fun Fragment.getGoogleSignInClient(): GoogleSignInClient {
    return (requireActivity().application as MyApplication).getGoogleSignInClient()
}

private fun hideKeyboard(activity: Activity, view: View) {
    val imm =
        activity.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}

fun getString(resID: Int): String = appContext.getString(resID)

fun getTypedArray(id: Int): TypedArray = appContext.resources.obtainTypedArray(id)

fun getContentResolver(): ContentResolver = appContext.contentResolver

/**
 * Returns true if the navigation controller is still pointing at 'this' fragment, or false if it
 * already navigated away.
 *
 * @link: https://stackoverflow.com/questions/51060762/illegalargumentexception-navigation-destination-xxx-is-unknown-to-this-navcontr
 */
fun Fragment.checkIfMayNavigate(destinationID: Int) {

    val navController = findNavController()
    val destinationIdInNavController = navController.currentDestination?.id

    // add tag_navigation_destination_id to your ids.xml so that it's unique:
    val destinationIdOfThisFragment =
        view?.getTag(R.id.tag_navigation_destination_id) ?: destinationIdInNavController

    // check that the navigation graph is still in 'this' fragment, if not then the app already navigated:
    return if (destinationIdInNavController == destinationIdOfThisFragment) {
        view?.setTag(R.id.tag_navigation_destination_id, destinationIdOfThisFragment)
        findNavController().navigate(destinationID)
    } else {
        Timber.d("May not navigate: current destination is not the current fragment.")
    }
}

inline fun Fragment.checkIfMayNavigate(action: () -> Unit) {

    val navController = findNavController()
    val destinationIdInNavController = navController.currentDestination?.id

    // add tag_navigation_destination_id to your ids.xml so that it's unique:
    val destinationIdOfThisFragment =
        view?.getTag(R.id.tag_navigation_destination_id) ?: destinationIdInNavController

    // check that the navigation graph is still in 'this' fragment, if not then the app already navigated:
    return if (destinationIdInNavController == destinationIdOfThisFragment) {
        view?.setTag(R.id.tag_navigation_destination_id, destinationIdOfThisFragment)
        action()
    } else {
        Timber.d("May not navigate: current destination is not the current fragment.")
    }
}
