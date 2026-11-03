package app.anchor.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.anchor.graph
import app.anchor.guard.GuardService
import app.anchor.ui.home.HomeScreen
import app.anchor.ui.onboarding.OnboardingFlow
import app.anchor.ui.theme.AnchorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnchorTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val config by graph.config.collectAsStateWithLifecycle()
                    val c = config
                    when {
                        c == null -> Box(Modifier.fillMaxSize())
                        !c.onboarded -> OnboardingFlow(c)
                        else -> HomeScreen(c)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Starting from the foreground is always allowed; keeps the guard alive after OEM kills.
        if (graph.config.value?.onboarded == true) GuardService.start(this)
    }
}
