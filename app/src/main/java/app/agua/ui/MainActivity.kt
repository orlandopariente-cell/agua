package app.agua.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.agua.update.UpdateDialog
import app.agua.update.rememberUpdateController
import app.agua.widget.WaterWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AguaTheme { AguaApp() } }
    }

    override fun onResume() {
        super.onResume()
        WaterWidget.updateAll(this)
        WaterWidget.scheduleTick(this)
    }
}

val StatusOk = Color(0xFF2E9E5B)
val StatusLow = Color(0xFFE5533D)

@Composable
fun AguaTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF64B5F6),
            onPrimary = Color(0xFF00315A),
            primaryContainer = Color(0xFF1C3A57),
            onPrimaryContainer = Color(0xFFD3E6FF),
            secondary = Color(0xFF8FCBE8),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1565C0),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEBFA),
            onPrimaryContainer = Color(0xFF0B2E52),
            secondary = Color(0xFF00838F),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AguaApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val updates = rememberUpdateController()
    LaunchedEffect(Unit) { updates.check(manual = false) }
    UpdateDialog(updates)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Hoje") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("Histórico") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Ajustes") },
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen()
                1 -> HistoryScreen()
                else -> SettingsScreen(updates)
            }
        }
    }
}
