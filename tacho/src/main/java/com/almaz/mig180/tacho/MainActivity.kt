package com.almaz.mig180.tacho

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.almaz.mig180.tacho.ui.TachoApp
import com.almaz.mig180.tacho.ui.theme.TachoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // В мастерской телефон стоит на подставке — экран гаснуть не должен.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            TachoTheme {
                TachoApp()
            }
        }
    }
}
