package com.nexusbudget.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.components.IconBadge
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> {
                Text("Nexus Budget", style = MaterialTheme.typography.displaySmall)
                Text(
                    "All your money in one place, a plan to get ahead, and an AI coach to help along the way.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Feature(Icons.Outlined.AccountBalance, "Every account at a glance", "Checking, cards, investments and student loans with your net worth and what's safe to spend.")
                Feature(Icons.Outlined.PieChart, "A budget that runs itself", "Automatic categories, bill and subscription tracking, and alerts before you overspend.")
                Feature(Icons.Outlined.Flag, "Plans that pay off", "Debt payoff dates, interest saved, savings goals and a 30-day cash forecast.")
                Feature(Icons.Outlined.AutoAwesome, "An AI money coach", "Ask anything. It looks up your real numbers and suggests changes you approve with a tap.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
            }
            1 -> {
                Text("Private by design", style = MaterialTheme.typography.headlineMedium)
                Feature(Icons.Outlined.Lock, "Read-only access", "Bank connections can see balances and transactions. They can never move money.")
                Feature(Icons.Outlined.PhoneAndroid, "Your data stays on your phone", "No company servers, no account to create. Credentials are encrypted by your phone's secure hardware.")
                Feature(Icons.Outlined.VisibilityOff, "No ads, no tracking", "Nothing is sold or shared. The AI assistant is optional and only used when you ask it something.")
                Feature(Icons.Outlined.Code, "Free and open source", "Anyone can read the code, check how it works, and build it themselves.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                TextButton(onClick = { step = 0 }) { Text("Back") }
            }
            else -> {
                Text("How do you want to start?", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "You can connect accounts now, add them by hand, or look around with a sample household first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("Set up my accounts") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            container.finance.loadDemo()
                            onFinished()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Explore with demo data") }
                TextButton(onClick = { step = 1 }) { Text("Back") }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        IconBadge(icon, size = 40)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
