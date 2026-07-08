package com.flowpay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.flowpay.app.utils.findComponentActivity

private val DarkColorScheme = darkColorScheme(
    primary = FlowpayTextWhite,
    secondary = FlowpayAccentBlue,
    tertiary = FlowpayAccentGreen,
    background = FlowpayBlack,
    surface = FlowpayDarkGray,
    onBackground = FlowpayTextWhite,
    onSurface = FlowpayTextWhite
)

/**
 * Flowpay is deliberately dark-only: the payment overlay, dialogs and home
 * screen are all designed against a black surface. Dynamic (Material You)
 * color is intentionally NOT used — on Android 12+ it silently replaced the
 * brand palette with wallpaper-derived colors. The old "light" scheme was
 * fake (black background with light accents) and has been removed.
 */
@Composable
fun FlowpayTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findComponentActivity()?.window ?: return@SideEffect
            // Set status bar to black to match the dark UI theme
            window.statusBarColor = android.graphics.Color.BLACK
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
