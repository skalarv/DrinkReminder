package com.drinkreminder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.drinkreminder.app.data.local.PreferencesManager
import com.drinkreminder.app.ui.navigation.AppNavigation
import com.drinkreminder.app.ui.theme.DarkModeOption
import com.drinkreminder.app.ui.theme.DrinkReminderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkMode by preferencesManager.darkModeOption
                .collectAsState(initial = DarkModeOption.SYSTEM)

            DrinkReminderTheme(darkModeOption = darkMode) {
                AppNavigation()
            }
        }
    }
}
