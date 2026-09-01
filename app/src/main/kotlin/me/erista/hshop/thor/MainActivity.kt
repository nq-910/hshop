package me.erista.hshop.thor

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.erista.hshop.thor.presentation.ThorBottomPresentation
import me.erista.hshop.thor.ui.MainViewModel
import me.erista.hshop.thor.ui.bottom.BottomScreenContent
import me.erista.hshop.thor.ui.theme.HShopThorTheme
import me.erista.hshop.thor.ui.top.TopScreenContent

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var bottomPresentation: ThorBottomPresentation? = null
    private lateinit var displayManager: DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            updatePresentations()
        }

        override fun onDisplayRemoved(displayId: Int) {
            updatePresentations()
        }

        override fun onDisplayChanged(displayId: Int) {
            updatePresentations()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on for handheld gaming experience
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        setContent {
            val settings by viewModel.settings.collectAsState()

            HShopThorTheme(appTheme = settings.theme) {
                val hasSecondaryDisplay = remember { mutableStateOf(getSecondaryDisplay() != null) }

                LaunchedEffect(Unit) {
                    updatePresentations()
                }

                if (hasSecondaryDisplay.value) {
                    // Running on Dual-Screen AYN Thor -> Top Screen handles Detail View
                    TopScreenContent(viewModel = viewModel)
                } else {
                    // Single screen / fallback mode -> Dual-Pane Split Layout
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopScreenContent(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1.1f)
                        )
                        HorizontalDivider(thickness = 2.dp)
                        BottomScreenContent(
                            viewModel = viewModel,
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                }
            }
        }
    }

    private fun getSecondaryDisplay(): Display? {
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (presentationDisplays.isNotEmpty()) {
            return presentationDisplays[0]
        }
        val allDisplays = displayManager.displays
        return allDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    private fun updatePresentations() {
        val secondaryDisplay = getSecondaryDisplay()
        if (secondaryDisplay != null) {
            if (bottomPresentation == null || bottomPresentation?.display?.displayId != secondaryDisplay.displayId) {
                bottomPresentation?.dismiss()
                bottomPresentation = ThorBottomPresentation(this, secondaryDisplay, viewModel).apply {
                    show()
                }
            }
        } else {
            bottomPresentation?.dismiss()
            bottomPresentation = null
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.navigateTitleUp()
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.navigateTitleDown()
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                viewModel.navigateSubcategoryPrev()
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                viewModel.navigateSubcategoryNext()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_L1,
            android.view.KeyEvent.KEYCODE_BUTTON_L2 -> {
                viewModel.navigateCategoryPrev()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_R1,
            android.view.KeyEvent.KEYCODE_BUTTON_R2 -> {
                viewModel.navigateCategoryNext()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_A,
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                viewModel.handleButtonA()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_B,
            android.view.KeyEvent.KEYCODE_BACK -> {
                viewModel.handleButtonB()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                viewModel.handleButtonX()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_Y -> {
                viewModel.handleButtonY()
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> {
                viewModel.handleButtonY()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK &&
            event.action == android.view.MotionEvent.ACTION_MOVE
        ) {
            val hatY = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y)
            val hatX = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X)
            val y = event.getAxisValue(android.view.MotionEvent.AXIS_Y)
            val x = event.getAxisValue(android.view.MotionEvent.AXIS_X)

            if (hatY < -0.5f || y < -0.5f) {
                viewModel.navigateTitleUp()
                return true
            } else if (hatY > 0.5f || y > 0.5f) {
                viewModel.navigateTitleDown()
                return true
            }

            if (hatX < -0.5f || x < -0.5f) {
                viewModel.navigateSubcategoryPrev()
                return true
            } else if (hatX > 0.5f || x > 0.5f) {
                viewModel.navigateSubcategoryNext()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        updatePresentations()
    }

    override fun onPause() {
        super.onPause()
        bottomPresentation?.dismiss()
        bottomPresentation = null
    }

    override fun onStop() {
        super.onStop()
        bottomPresentation?.dismiss()
        bottomPresentation = null
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.unregisterDisplayListener(displayListener)
        bottomPresentation?.dismiss()
        bottomPresentation = null
    }

    fun dismissBottomPresentation() {
        bottomPresentation?.dismiss()
        bottomPresentation = null
    }
}
