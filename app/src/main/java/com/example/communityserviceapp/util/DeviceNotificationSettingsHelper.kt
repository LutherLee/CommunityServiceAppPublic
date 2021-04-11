package com.example.communityserviceapp.util

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.communityserviceapp.ui.MyApplication.Companion.appContext
import com.example.communityserviceapp.util.Constants.AUTOSTART_APP_PERMISSION
import com.example.communityserviceapp.util.Constants.DISPLAY_POP_UP_WINDOWS_APP_PERMISSION
import timber.log.Timber
import javax.inject.Inject

/**
 * A class that is used to help to fix unable to receive notification problem for certain phone brand.
 *
 * Problem: WorkManager that is used to show notification cannot work properly with custom ROM
 * from brand like "XiaoMi", "Oppo", "Vivo", "Letv", "Honor", "OnePlus". The reason is due to
 * battery saving feature imposed by manufacturer that by default don't allow app to auto start
 * or show notification when app is killed (not running in background).
 *
 * Solution: Two permission is required: "Autostart" and "Pop-up/Floating Window". No workaround to
 * enable such permission programmatically, have to be manually enabled by user.
 *
 * See: https://github.com/googlecodelabs/android-workmanager/issues/22
 * See: https://stackoverflow.com/questions/41524459/broadcast-receiver-not-working-after-device-reboot-in-android
 */
class DeviceNotificationSettingsHelper @Inject constructor() {

    fun checkDeviceBrand(permissionType: Int) {
        try {
            val intent = Intent()
            val manufacturer = Build.MANUFACTURER
            checkIfDeviceBrandIsXiaomi(manufacturer, permissionType, intent)
            // Check if permission type is "DISPLAY_POP_UP_WINDOWS" and if it is empty
            val list = appContext.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            if (list.isNotEmpty()) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            }
        } catch (e: Exception) {
            Timber.e("DeviceNotificationSettings = $e")
        }
    }

    private fun checkIfDeviceBrandIsXiaomi(
        manufacturer: String,
        permissionType: Int,
        intent: Intent
    ) {
        var customIntent = intent
        if ("xiaomi".equals(manufacturer, ignoreCase = true)) {
            if (permissionType == AUTOSTART_APP_PERMISSION) {
                customIntent.component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            } else if (permissionType == DISPLAY_POP_UP_WINDOWS_APP_PERMISSION) {
                customIntent = Intent("miui.intent.action.APP_PERM_EDITOR")
                customIntent.setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                customIntent.putExtra("extra_pkgname", appContext.packageName)
                customIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(customIntent)
            }
            Timber.d("Device brand is Xiaomi")
        } else {
            checkIfDeviceBrandIsHonor(manufacturer, permissionType, customIntent)
        }
    }

    private fun checkIfDeviceBrandIsHonor(
        manufacturer: String,
        permissionType: Int,
        intent: Intent
    ) {
        if ("Honor".equals(manufacturer, ignoreCase = true)) {
            if (permissionType == AUTOSTART_APP_PERMISSION) {
                openAppDetailsSettingIntent(intent)
            } else if (permissionType == DISPLAY_POP_UP_WINDOWS_APP_PERMISSION) {
                intent.component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
            Timber.d("Device brand is Honor")
        } else {
            checkIfDeviceBrandIsOppo(manufacturer, permissionType, intent)
        }
    }

    private fun checkIfDeviceBrandIsOppo(
        manufacturer: String,
        permissionType: Int,
        intent: Intent
    ) {
        if ("oppo".equals(manufacturer, ignoreCase = true)) {
            if (permissionType == AUTOSTART_APP_PERMISSION) {
                openAppDetailsSettingIntent(intent)
            } else if (permissionType == DISPLAY_POP_UP_WINDOWS_APP_PERMISSION) {
                intent.component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
            }
            Timber.d("Device brand is Oppo")
        } else {
            checkIfDeviceBrandIsVivo(manufacturer, permissionType, intent)
        }
    }

    private fun checkIfDeviceBrandIsVivo(
        manufacturer: String,
        permissionType: Int,
        intent: Intent
    ) {
        if ("vivo".equals(manufacturer, ignoreCase = true)) {
            if (permissionType == AUTOSTART_APP_PERMISSION) {
                openAppDetailsSettingIntent(intent)
            } else if (permissionType == DISPLAY_POP_UP_WINDOWS_APP_PERMISSION) {
                intent.component = ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            Timber.d("Device brand is Vivo")
        } else {
            checkIfDeviceBrandIsOnePlus(manufacturer, permissionType, intent)
        }
    }

    private fun checkIfDeviceBrandIsOnePlus(
        manufacturer: String,
        permissionType: Int,
        intent: Intent
    ) {
        if ("oneplus".equals(manufacturer, ignoreCase = true)) {
            if (permissionType == AUTOSTART_APP_PERMISSION) {
                openAppDetailsSettingIntent(intent)
            } else if (permissionType == DISPLAY_POP_UP_WINDOWS_APP_PERMISSION) {
                intent.component = ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity‌​"
                )
            }
            Timber.d("Device brand is OnePlus")
        } else {
            checkIfDeviceBrandIsLetv(manufacturer, permissionType, intent)
        }
    }

    private fun checkIfDeviceBrandIsLetv(
        manufacturer: String,
        permissionType: Int,
        intent: Intent
    ) {
        if ("Letv".equals(manufacturer, ignoreCase = true)) {
            if (permissionType == AUTOSTART_APP_PERMISSION) {
                openAppDetailsSettingIntent(intent)
            } else if (permissionType == DISPLAY_POP_UP_WINDOWS_APP_PERMISSION) {
                intent.component = ComponentName(
                    "com.letv.android.letvsafe",
                    "com.letv.android.letvsafe.AutobootManageActivity"
                )
            }
            Timber.d("Device brand is Letv")
        }
    }

    private fun openAppDetailsSettingIntent(intent: Intent) {
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", appContext.packageName, null)
    }
}
