package me.erista.hshop.thor.presentation

import android.app.Presentation
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.erista.hshop.thor.ui.MainViewModel
import me.erista.hshop.thor.ui.bottom.BottomScreenContent
import me.erista.hshop.thor.ui.theme.HShopThorTheme

class ThorBottomPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val viewModel: MainViewModel
) : Presentation(activity, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(activity)
            decor.setViewTreeViewModelStoreOwner(activity)
            decor.setViewTreeSavedStateRegistryOwner(activity)
        }

        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val settings by viewModel.settings.collectAsState()
                HShopThorTheme(appTheme = settings.theme) {
                    BottomScreenContent(viewModel = viewModel)
                }
            }
        }

        setContentView(composeView)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        // Prevent dialog dismissal; route back action through ViewModel navigation or activity back press
        if (!viewModel.handleButtonB()) {
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) {
            if (viewModel.handleButtonB()) {
                return true
            }
        }
        if (activity.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (activity.onGenericMotionEvent(event)) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
