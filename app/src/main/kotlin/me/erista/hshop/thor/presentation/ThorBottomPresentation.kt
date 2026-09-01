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
}
