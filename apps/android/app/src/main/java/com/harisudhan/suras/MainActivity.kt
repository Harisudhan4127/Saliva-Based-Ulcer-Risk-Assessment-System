package com.harisudhan.suras

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harisudhan.suras.ui.AppViewModel
import com.harisudhan.suras.ui.SurasApp
import com.harisudhan.suras.ui.theme.SurasTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Block screenshots and screen recordings of patient data. This is a
        // defense-in-depth control, not a guarantee: rooted devices and
        // external cameras cannot be controlled by the app.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val openSettings = intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true

        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            SurasTheme(themeMode = settings.themeMode) {
                SurasApp(startAtSettings = openSettings)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // A notification tap while the activity is alive is handled by
        // recreating the activity with the new intent; Compose state is
        // restored from the saved instance state.
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            setIntent(intent)
            recreate()
        }
    }

    companion object {
        const val EXTRA_OPEN_SETTINGS = "com.harisudhan.suras.OPEN_SETTINGS"
    }
}
