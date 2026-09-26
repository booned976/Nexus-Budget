package com.nexusbudget.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Tabs spread evenly across the screen. Each tab is as wide as its label, so labels are never cut
 * off; when they don't all fit (a narrow phone or a large font), the row scrolls sideways and keeps
 * the selected tab in view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FitTabRow(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val indicatorColor = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("subtabs")) {
            val viewport = maxWidth
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = viewport)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                titles.forEachIndexed { index, title ->
                    val isSelected = index == selected
                    val requester = remember { BringIntoViewRequester() }
                    LaunchedEffect(isSelected) { if (isSelected) requester.bringIntoView() }
                    Tab(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = { Text(title, maxLines = 1) },
                        modifier = Modifier
                            .testTag("subtab-$index")
                            .bringIntoViewRequester(requester)
                            .widthIn(min = 72.dp)
                            .drawWithContent {
                                drawContent()
                                if (isSelected) {
                                    // Underline the selected tab, like the standard tab indicator.
                                    val inset = 12.dp.toPx()
                                    val height = 3.dp.toPx()
                                    drawRoundRect(
                                        color = indicatorColor,
                                        topLeft = Offset(inset, size.height - height),
                                        size = Size(size.width - inset * 2, height),
                                        cornerRadius = CornerRadius(height, height),
                                    )
                                }
                            },
                    )
                }
            }
        }
        HorizontalDivider()
    }
}
