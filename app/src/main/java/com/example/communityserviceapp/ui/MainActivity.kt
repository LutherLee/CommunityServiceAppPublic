package com.example.communityserviceapp.ui

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.Fade
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.example.communityserviceapp.R
import com.example.communityserviceapp.data.repository.detachAllFirebaseRealTimeListener
import com.example.communityserviceapp.databinding.ActivityMainBinding
import com.example.communityserviceapp.util.Constants.AUTOSTART_APP_PERMISSION
import com.example.communityserviceapp.util.Constants.DISPLAY_POP_UP_WINDOWS_APP_PERMISSION
import com.example.communityserviceapp.util.DeviceNotificationSettingsHelper
import com.example.communityserviceapp.util.subscribeToFCMTopic
import com.google.android.material.appbar.AppBarLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var deviceNotificationSettingsHelper: DeviceNotificationSettingsHelper
    @Inject
    lateinit var sharedPreferences: SharedPreferences
    @Inject
    lateinit var editor: SharedPreferences.Editor
    private var deviceNotificationSettingsAlertDialog: AlertDialog? = null
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    private lateinit var binding: ActivityMainBinding
    private var pressAgainToExitToast: Toast? = null
    private var allowBackPress = true
    private var isDrawerLayoutOpened = false
    private var doubleBackToExitPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme) // Hide the splash theme
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        setupNavigationController()
        checkDefaultFCMTopicSubscriptionStatus()
        checkIfAlreadyAskToSetupDeviceNotificationSettings()
        setupBottomNavFadeTransitionAnimation()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.IO) { mainViewModel.attachFirebaseListenerOnAppStart() }
    }

    override fun onPause() {
        pressAgainToExitToast?.cancel()
        super.onPause()
    }

    override fun onStop() {
        deviceNotificationSettingsAlertDialog?.dismiss()
        detachAllFirebaseRealTimeListener()
        super.onStop()
    }

    private fun setupNavigationController() {
        navController = findNavController(R.id.nav_host_fragment)
        NavigationUI.setupWithNavController(binding.bottomNav, navController)

        // Prevent BottomNavView from recreating fragment if it already exists
        // By doing nothing in the method body, we ignore reselection events
        // See: https://stackoverflow.com/questions/56212128/question-about-android-navigation-architecture-component
        binding.bottomNav.setOnNavigationItemReselectedListener {
            when (it.itemId) {
                R.id.homeFragment -> {
                    findViewById<NestedScrollView>(R.id.home_nestedscrollview).smoothScrollTo(0, 0)
                    findViewById<AppBarLayout>(R.id.home_appBarLayout).setExpanded(true)
                }
            }
        }

        navController.addOnDestinationChangedListener { controller: NavController, destination: NavDestination, arguments: Bundle? ->
            when (destination.id) {
                R.id.homeFragment -> {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    showBottomNavigationView()
                }
                R.id.profileFragment -> {
                    window.statusBarColor = getColor(R.color.transparent)
                    showBottomNavigationView()
                }
                R.id.mapFragment -> {
                    window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    hideBottomNavigationView()
                }
                R.id.loginFragment, R.id.feedbackFragment,
                R.id.announcementFragment, R.id.recipientSearchFragment, R.id.faqFragment,
                R.id.setRecommendationCriteriaFragment, R.id.recipientFavoriteFragment -> {
                    hideBottomNavigationView()
                }
            }
        }
    }

    private fun checkDefaultFCMTopicSubscriptionStatus() {
        checkIfSubscribedToAnnouncementsFCMTopic()
//        checkIfSubscribedToRemindersFCMTopic()
    }

    private fun checkIfSubscribedToAnnouncementsFCMTopic() {
        val hasSubscribedToAnnouncementsTopic =
            sharedPreferences.getBoolean(getString(R.string.has_announcement_subscription), false)
        if (!hasSubscribedToAnnouncementsTopic) {
            subscribeToAnnouncementsFCMTopic()
        }
    }

    private fun subscribeToAnnouncementsFCMTopic() {
        lifecycleScope.launch {
            val isSuccessful = withContext(Dispatchers.IO) {
                subscribeToFCMTopic("Announcements")
            }
            if (isSuccessful) {
                editor.putBoolean(getString(R.string.has_announcement_subscription), true).commit()
                Timber.d("Successfully subscribed to Announcements topic")
            } else {
                Timber.d("Failed to subscribe to Announcements topic. Retrying...")
                subscribeToAnnouncementsFCMTopic()
            }
        }
    }

    private fun checkIfSubscribedToRemindersFCMTopic() {
        val hasSubscribedToRemindersTopic =
            sharedPreferences.getBoolean(getString(R.string.has_reminder_subscription), false)
        if (!hasSubscribedToRemindersTopic) {
            subscribeToRemindersFCMTopic()
        }
    }

    private fun subscribeToRemindersFCMTopic() {
        lifecycleScope.launch {
            val isSuccessful = withContext(Dispatchers.IO) {
                subscribeToFCMTopic("Reminders")
            }
            if (isSuccessful) {
                editor.putBoolean(getString(R.string.has_reminder_subscription), true).commit()
                Timber.d("Successfully subscribed to Reminders topic")
            } else {
                Timber.d("Failed to subscribe to Reminders topic. Retrying...")
                subscribeToRemindersFCMTopic()
            }
        }
    }

    private fun checkIfAlreadyAskToSetupDeviceNotificationSettings() {
        val hasAskedToSetupDeviceNotificationSettings = sharedPreferences.getBoolean(
            getString(R.string.has_asked_to_setup_device_notification_settings), false
        )
        if (!hasAskedToSetupDeviceNotificationSettings) {
            showDeviceNotificationSettingsAlertDialog()
        }
    }

    private fun showDeviceNotificationSettingsAlertDialog() {
        deviceNotificationSettingsAlertDialog = AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage(getString(R.string.xiaomi_notification_related_permission_needed_message))
            .setPositiveButton("Enable Autostart Permission") { dialogInterface: DialogInterface?, i: Int ->
                deviceNotificationSettingsHelper.checkDeviceBrand(AUTOSTART_APP_PERMISSION)
            }
            .setNegativeButton("Enable Pop-up Windows") { dialogInterface: DialogInterface?, i: Int ->
                deviceNotificationSettingsHelper.checkDeviceBrand(
                    DISPLAY_POP_UP_WINDOWS_APP_PERMISSION
                )
            }
            // Since there is no way to check if autostart permission for these brands, we
            // show this message every time app startup until user choose to never show it again
            .setNeutralButton("Never Show Again") { dialogInterface: DialogInterface, i: Int ->
                editor.putBoolean(
                    getString(R.string.has_asked_to_setup_device_notification_settings),
                    true
                ).commit()
                dialogInterface.dismiss()
            }
            .show()
    }

    override fun onBackPressed() {
        // Check if current destination is home fragment
        if (navController.currentDestination!!.id == R.id.homeFragment) {
            if (isDrawerLayoutOpened) {
                super.onBackPressed()
            } else {
                // Check if only press back once within 2000ms and if back press is allowed
                if (allowBackPress && doubleBackToExitPressedOnce) {
                    finish() // force close activity
                } else {
                    doubleBackToExitPressedOnce = true
                    pressAgainToExitToast =
                        Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT)
                    pressAgainToExitToast?.show()
                    // Note: memory leak may occur
                    Handler(Looper.getMainLooper()).postDelayed(
                        {
                            doubleBackToExitPressedOnce = false
                        },
                        2000
                    )
                }
            }
        } else {
            if (allowBackPress) {
                super.onBackPressed()
            }
        }
    }

    private fun setupBottomNavFadeTransitionAnimation() {
        val transition = Fade().setDuration(50).addTarget(binding.bottomNav)
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)
    }

    fun hideBottomNavigationView() {
        binding.bottomNav.visibility = View.GONE
        isDrawerLayoutOpened = true
    }

    private fun showBottomNavigationView() {
        binding.bottomNav.visibility = View.VISIBLE
        isDrawerLayoutOpened = false
    }

    fun disableUIInteraction() {
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        allowBackPress = false
    }

    fun enableUIInteraction() {
        this.window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        allowBackPress = true
    }
}
