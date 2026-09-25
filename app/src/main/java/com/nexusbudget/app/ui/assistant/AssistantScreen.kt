package com.nexusbudget.app.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nexusbudget.app.data.ChatMessage
import com.nexusbudget.app.data.ProposalState
import com.nexusbudget.app.ui.LocalAppContainer
import com.nexusbudget.app.ui.Routes
import com.nexusbudget.app.ui.ScreenScaffold
import com.nexusbudget.app.ui.components.IconBadge
import com.nexusbudget.app.ui.components.MoneyText
import com.nexusbudget.app.ui.components.NexusCard
import com.nexusbudget.app.ui.components.money
import com.nexusbudget.app.ui.long
import com.nexusbudget.app.ui.openUrl
import com.nexusbudget.assistant.ChatRole
import com.nexusbudget.assistant.Proposal
import kotlinx.coroutines.launch

private val suggestions = listOf(
    "Make me a plan to pay off my debt faster",
    "Where did my money go this month?",
    "How big should my emergency fund be?",
    "Build me a realistic budget",
    "Which subscriptions should I cut?",
    "Can I afford a \$500 purchase this month?",
    "How should I handle my student loans?",
    "Am I on track for my goals?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(nav: NavHostController) {
    val container = LocalAppContainer.current
    val repo = container.assistant
    val messages by repo.messages.collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by repo.progress.collectAsStateWithLifecycle()
    val pending by container.pendingAssistantPrompt.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val hasKey by repo.hasKey.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(pending, hasKey) {
        val prompt = pending
        if (prompt != null && hasKey) {
            container.pendingAssistantPrompt.value = null
            repo.send(prompt)
        } else if (prompt != null) {
            input = prompt
            container.pendingAssistantPrompt.value = null
        }
    }
    LaunchedEffect(messages.size, progress?.text?.length, progress?.status) {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    ScreenScaffold(
        title = "Ask AI",
        topLevel = true,
        actions = {
            if (messages.isNotEmpty()) {
                IconButton(onClick = { scope.launch { repo.clear() } }) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "New chat") }
            }
            IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, contentDescription = "AI settings") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (!hasKey) {
                ApiKeySetup(onSaved = { key -> repo.saveApiKey(key) })
                return@Column
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty() && progress == null) {
                    item {
                        Column {
                            IconBadge(Icons.Outlined.AutoAwesome, size = 48)
                            Spacer(Modifier.height(12.dp))
                            Text("Your AI money coach", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Ask about your spending, debts, goals or any money question. The assistant looks up your real numbers " +
                                    "and can suggest budget changes or goals you apply with one tap.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { suggestion ->
                                    SuggestionChip(onClick = { repo.send(suggestion) }, label = { Text(suggestion) })
                                }
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message,
                        onApply = { scope.launch { repo.applyProposal(message) } },
                        onDismiss = { scope.launch { repo.dismissProposal(message) } },
                    )
                }
                progress?.let { live ->
                    item(key = "live-question") {
                        if (messages.lastOrNull()?.text != live.question) Bubble(live.question, user = true)
                    }
                    item(key = "live-answer") {
                        Column {
                            if (live.text.isNotEmpty()) Bubble(live.text, user = false)
                            live.status?.let { status ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("$status…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            InputBar(
                value = input,
                onValueChange = { input = it },
                busy = progress != null,
                onSend = {
                    repo.send(input)
                    input = ""
                },
                onStop = { repo.stop() },
            )
        }
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, busy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Ask about your money…") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (busy) {
                IconButton(onClick = onStop) { Icon(Icons.Outlined.Stop, contentDescription = "Stop") }
            } else {
                IconButton(onClick = onSend, enabled = value.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
            }
        }
        Text(
            "AI can make mistakes and isn't professional financial advice.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        )
    }
}

@Composable
private fun Bubble(text: String, user: Boolean, error: Boolean = false) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        val background = when {
            error -> MaterialTheme.colorScheme.errorContainer
            user -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
        val foreground = when {
            error -> MaterialTheme.colorScheme.onErrorContainer
            user -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (user) Text(text, color = foreground, style = MaterialTheme.typography.bodyLarge) else MarkdownText(text, color = foreground)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onApply: () -> Unit, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.text.isNotBlank()) Bubble(message.text, user = message.role == ChatRole.USER, error = message.isError)
        message.proposal?.let { ProposalCard(it, message.proposalState, onApply, onDismiss) }
    }
}

@Composable
private fun ProposalCard(proposal: Proposal, state: ProposalState?, onApply: () -> Unit, onDismiss: () -> Unit) {
    NexusCard {
        when (proposal) {
            is Proposal.BudgetChanges -> {
                Text("Suggested budget changes", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                proposal.changes.forEach { change ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(change.categoryName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        change.previousAmount?.let {
                            Text("${money(it)} → ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        MoneyText(change.monthlyAmount, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is Proposal.NewGoal -> {
                Text("Suggested goal: ${proposal.type.icon} ${proposal.name}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Save ${money(proposal.target)} at ${money(proposal.monthlyContribution)}/month" +
                        (proposal.targetDate?.let { " by ${it.long()}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (proposal.rationale.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(proposal.rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        when (state) {
            ProposalState.APPLIED -> Text("✓ Applied", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ProposalState.DISMISSED -> Text("Dismissed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onApply) { Text("Apply") }
                TextButton(onClick = onDismiss) { Text("No thanks") }
            }
        }
    }
}

@Composable
private fun ApiKeySetup(onSaved: (String) -> Unit) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBadge(Icons.Outlined.AutoAwesome, size = 48)
        Text("Connect Claude to get an AI money coach", style = MaterialTheme.typography.titleLarge)
        Text(
            "The coach is optional. Budgets, plans and recommendations all work without it. It uses Claude, by Anthropic, " +
                "with your own API key: there's no subscription, Anthropic bills you only for the questions you ask, " +
                "and you can set a spending limit in your Anthropic account.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Privacy: questions and the numbers the assistant looks up are sent to Anthropic to generate answers. " +
                "Account numbers are never shared, and you can limit it to totals only in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { openUrl(context, "https://console.anthropic.com/settings/keys") }) { Text("Create an API key") }
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim() },
            label = { Text("Anthropic API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSaved(key) }, enabled = key.length > 20, modifier = Modifier.fillMaxWidth()) { Text("Save key") }
        Text("The key is encrypted with your phone's secure hardware.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
