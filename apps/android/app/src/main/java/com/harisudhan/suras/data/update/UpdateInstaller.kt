package com.harisudhan.suras.data.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands a verified APK to the system package installer.
 *
 * The app never installs silently and never executes the downloaded file. The
 * user must grant "install unknown apps" for SURAS and confirm the install in
 * the system UI. No `REQUEST_INSTALL_PACKAGES` bypass exists here.
 */
@Singleton
class UpdateInstaller @Inject constructor(@ApplicationContext context: Context) {

    private val appContext = context.applicationContext

    fun canInstallPackages(): Boolean =
        appContext.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    /** @return true when the system installer was opened. */
    fun openInstaller(apk: File): Boolean {
        if (!apk.isFile || apk.length() == 0L) return false
        val updateDir = File(appContext.cacheDir, "updates").canonicalFile
        val canonical = apk.canonicalFile
        if (!canonical.path.startsWith(updateDir.path + File.separator)) return false
        if (!canonical.name.endsWith(".apk", ignoreCase = true)) return false

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            canonical,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("suras-update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        return true
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
