package com.offlinevault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinevault.app.data.model.ThemeMode
import com.offlinevault.app.ui.OfflineVaultRoot
import com.offlinevault.app.ui.onboarding.OnboardingScreen
import com.offlinevault.app.ui.theme.OfflineVaultTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Screenshots, screen recording and the recent-apps thumbnail are blocked
        // for every screen in the app, including the setup flow.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val container = (application as OfflineVaultApplication).container

        setContent {
            val settings by container.settingsRepository.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            OfflineVaultTheme(darkTheme = darkTheme) {
                var onboardingComplete by remember {
                    mutableStateOf(container.securePrefs.isOnboardingComplete())
                }

                if (!onboardingComplete) {
                    OnboardingScreen(
                        onSetupComplete = { onboardingComplete = true },
                    )
                } else {
                    OfflineVaultRoot(
                        container = container,
                        onVaultWiped = { onboardingComplete = false },
                    )
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as? OfflineVaultApplication)?.container?.sessionManager?.onUserInteraction()
    }
}
