package kr.mysterydrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import kr.mysterydrive.ui.MysteryApp
import kr.mysterydrive.ui.MysteryTheme
import kr.mysterydrive.ui.TripViewModel

class MainActivity : ComponentActivity() {
    private val vm: TripViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MysteryTheme { MysteryApp(vm) } }
    }
}
