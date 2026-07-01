package com.cogelasuave.service

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Device-admin receiver. Being an *active* device admin makes the app
 * non-uninstallable until the admin is deactivated in Settings — and while
 * strict mode is on, [InterceptAccessibilityService] bounces the user out of the
 * deactivation/uninstall screens, so removal is effectively blocked for the lock
 * duration.
 *
 * We hold no real management policy; the registration alone is what we want.
 */
class CogelaSuaveAdminReceiver : DeviceAdminReceiver() {

    /** Shown by the system on the deactivation screen. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(com.cogelasuave.R.string.admin_disable_warning)

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context.applicationContext, CogelaSuaveAdminReceiver::class.java)

        fun isActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isAdminActive(component(context))
        }

        /** Intent that opens the system "activate device admin" confirmation screen. */
        fun enableIntent(context: Context): Intent =
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    context.getString(com.cogelasuave.R.string.admin_enable_explanation),
                )
    }
}
