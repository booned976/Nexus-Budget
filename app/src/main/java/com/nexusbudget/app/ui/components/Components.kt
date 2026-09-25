package com.nexusbudget.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusbudget.app.ui.theme.LocalChartColors
import com.nexusbudget.app.ui.theme.LocalHideAmounts
import com.nexusbudget.core.engine.BudgetHealth
import com.nexusbudget.core.engine.Severity
import com.nexusbudget.core.model.AccountGroup
import com.nexusbudget.core.model.AccountType
import com.nexusbudget.core.model.Money

/** Formats money, masking it when privacy mode is on. */
@Composable
fun money(cents: Long, showCents: Boolean = false, signed: Boolean = false, compact: Boolean = false): String = when {
    LocalHideAmounts.current -> "••••"
    compact -> Money.formatCompact(cents)
    signed -> Money.formatSigned(cents)
    else -> Money.format(cents, showCents = showCents)
}

@Composable
fun MoneyText(
    cents: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    showCents: Boolean = false,
    signed: Boolean = false,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    Text(money(cents, showCents, signed), modifier = modifier, style = style, color = color, fontWeight = fontWeight, maxLines = 1)
}

/** Amount color for transactions: income reads green, spending uses the normal text color. */
@Composable
fun amountColor(cents: Long): Color = if (cents > 0) LocalChartColors.current.positiveText else MaterialTheme.colorScheme.onSurface

@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, border = border) {
            Column(Modifier.padding(contentPadding.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, border = border) {
            Column(Modifier.padding(contentPadding.dp), content = content)
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Horizontal meter: the fill carries state, the track is a lighter step of the same ramp. */
@Composable
fun ProgressMeter(progress: Float, modifier: Modifier = Modifier, color: Color = LocalChartColors.current.series1, height: Int = 8) {
    val track = LocalChartColors.current.track
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(track),
    ) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .height(height.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
fun healthColor(health: BudgetHealth): Color {
    val c = LocalChartColors.current
    return when (health) {
        BudgetHealth.OVER -> c.critical
        BudgetHealth.WATCH -> c.warning
        BudgetHealth.ON_TRACK -> c.series1
        BudgetHealth.NO_BUDGET -> c.baseline
    }
}

/** Status is never color alone: an icon and a short label always accompany it. */
@Composable
fun HealthLabel(health: BudgetHealth, modifier: Modifier = Modifier) {
    val c = LocalChartColors.current
    val (icon, label, tint) = when (health) {
        BudgetHealth.OVER -> Triple(Icons.Outlined.ErrorOutline, "Over", c.critical)
        BudgetHealth.WATCH -> Triple(Icons.Outlined.Warning, "Watch", c.warning)
        BudgetHealth.ON_TRACK -> Triple(Icons.Outlined.CheckCircle, "On track", c.good)
        BudgetHealth.NO_BUDGET -> Triple(Icons.Outlined.Info, "No budget", c.baseline)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SeverityIcon(severity: Severity, modifier: Modifier = Modifier) {
    val c = LocalChartColors.current
    val (icon, tint, label) = when (severity) {
        Severity.URGENT -> Triple(Icons.Outlined.ErrorOutline, c.critical, "Urgent")
        Severity.IMPORTANT -> Triple(Icons.Outlined.Warning, c.serious, "Important")
        Severity.SUGGESTION -> Triple(Icons.Outlined.Info, c.series1, "Suggestion")
        Severity.POSITIVE -> Triple(Icons.Outlined.CheckCircle, c.good, "Good news")
    }
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun EmojiBadge(emoji: String, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size * 0.45f).sp)
    }
}

fun accountIcon(type: AccountType): ImageVector = when (type) {
    AccountType.CHECKING, AccountType.CASH -> Icons.Outlined.Payments
    AccountType.SAVINGS -> Icons.Outlined.Savings
    AccountType.CREDIT_CARD, AccountType.LINE_OF_CREDIT -> Icons.Outlined.CreditCard
    AccountType.BROKERAGE, AccountType.CRYPTO, AccountType.HSA -> Icons.AutoMirrored.Outlined.ShowChart
    AccountType.RETIREMENT -> Icons.Outlined.WorkOutline
    AccountType.STUDENT_LOAN -> Icons.Outlined.School
    AccountType.AUTO_LOAN -> Icons.Outlined.DirectionsCar
    AccountType.MORTGAGE, AccountType.PROPERTY -> Icons.Outlined.House
    else -> Icons.Outlined.AccountBalance
}

fun groupIcon(group: AccountGroup): ImageVector = when (group) {
    AccountGroup.CASH -> Icons.Outlined.Payments
    AccountGroup.CREDIT -> Icons.Outlined.CreditCard
    AccountGroup.INVESTMENTS -> Icons.AutoMirrored.Outlined.ShowChart
    AccountGroup.LOANS -> Icons.Outlined.School
    AccountGroup.PROPERTY -> Icons.Outlined.House
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary, size: Int = 40) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, content = trailing)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, size = 56)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Text field for dollar amounts; accepts digits and a decimal point. */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    allowNegative: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            val filtered = text.filterIndexed { i, ch -> ch.isDigit() || ch == '.' || ch == ',' || (allowNegative && ch == '-' && i == 0) }
            onValueChange(filtered)
        },
        label = { Text(label) },
        prefix = { Text("$") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = supportingText?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Cents -> editable text without symbols ("1234.50"). */
fun centsToInput(cents: Long?): String = cents?.let { Money.toPlainString(it).removeSuffix(".00") } ?: ""

fun inputToCents(text: String): Long? = Money.parse(text.replace(",", ""))

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer, contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
