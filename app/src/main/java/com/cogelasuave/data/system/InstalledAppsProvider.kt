package com.cogelasuave.data.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.cogelasuave.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Reads the set of user-launchable apps from [PackageManager]. */
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Returns every app that exposes a launcher activity, except this app itself,
     * sorted by visible label. Watched-state fields are left at their defaults here;
     * the repository merges in the persisted state.
     */
    suspend fun queryLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        resolved.asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { pkg ->
                AppInfo(
                    packageName = pkg,
                    label = loadLabel(pm, pkg),
                    isWatched = false,
                    customWaitSeconds = null,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun loadLabel(pm: PackageManager, pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
