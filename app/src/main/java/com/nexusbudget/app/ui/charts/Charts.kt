package com.nexusbudget.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusbudget.app.ui.theme.LocalChartColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class ChartSeries(val label: String, val values: List<Double>, val color: Color)

/** Rounds an axis step to 1, 2, 2.5 or 5 times a power of ten. */
private fun niceStep(range: Double, ticks: Int): Double {
    if (range <= 0.0) return 1.0
    val raw = range / ticks
    val magnitude = 10.0.pow(floor(log10(raw)))
    val residual = raw / magnitude
    val nice = when {
        residual <= 1.0 -> 1.0
        residual <= 2.0 -> 2.0
        residual <= 2.5 -> 2.5
        residual <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

private data class Axis(val min: Double, val max: Double, val ticks: List<Double>)

private fun axisFor(values: List<Double>, includeZero: Boolean): Axis {
    var lo = values.minOrNull() ?: 0.0
    var hi = values.maxOrNull() ?: 1.0
    if (includeZero) {
        lo = minOf(lo, 0.0)
        hi = maxOf(hi, 0.0)
    }
    if (lo == hi) {
        lo -= 1.0
        hi += 1.0
    }
    val step = niceStep(hi - lo, 3)
    val min = floor(lo / step) * step
    val max = ceil(hi / step) * step
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
    return Axis(min, max, ticks)
}

@Composable
private fun Legend(series: List<ChartSeries>) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(50)).background(s.color))
                Spacer(Modifier.width(6.dp))
                Text(s.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Line chart with a hairline grid, 2dp lines, a 10% area wash for a single series, end dots with a
 * surface ring, and touch scrubbing (press or drag) that shows a crosshair and tooltip.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    xLabel: (Int) -> String,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Int = 180,
    includeZero: Boolean = false,
    description: String = "",
    markLowest: Boolean = false,
) {
    val colors = LocalChartColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    val measurer = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    val count = series.maxOfOrNull { it.values.size } ?: 0
    if (count < 2) return
    val axis = axisFor(series.flatMap { it.values }, includeZero)
    val labelStyle = TextStyle(fontSize = 11.sp, color = muted)

    Column(modifier.semantics { if (description.isNotEmpty()) contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .pointerInput(series) {
                    detectTapGestures(onPress = { offset ->
                        touchX = offset.x
                        tryAwaitRelease()
                        touchX = null
                    })
                }
                .pointerInput(series) {
                    detectHorizontalDragGestures(
                        onDragStart = { touchX = it.x },
                        onDragEnd = { touchX = null },
                        onDragCancel = { touchX = null },
                    ) { change, _ -> touchX = change.position.x }
                },
        ) {
            val tickLabels = axis.ticks.map { measurer.measure(valueLabel(it), labelStyle) }
            val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + 8.dp.toPx()
            val bottomLabelHeight = 18.dp.toPx()
            val top = 8.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val bottom = size.height - bottomLabelHeight
            val plotWidth = right - left
            val plotHeight = bottom - top
            fun x(i: Int) = left + plotWidth * i / (count - 1)
            fun y(v: Double) = (bottom - (v - axis.min) / (axis.max - axis.min) * plotHeight).toFloat()

            // Recessive hairline grid with tick labels.
            axis.ticks.forEachIndexed { i, tick ->
                val ty = y(tick)
                drawLine(if (tick == 0.0) colors.baseline else colors.grid, Offset(left, ty), Offset(right, ty), strokeWidth = 1f)
                val label = tickLabels[i]
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), ty - label.size.height / 2))
            }
            // X labels: first, middle, last.
            listOf(0, (count - 1) / 2, count - 1).distinct().forEach { i ->
                val text = measurer.measure(xLabel(i), labelStyle)
                val tx = (x(i) - text.size.width / 2).coerceIn(left, right - text.size.width)
                drawText(text, topLeft = Offset(tx, bottom + 4.dp.toPx()))
            }

            series.forEach { s ->
                if (s.values.size < 2) return@forEach
                val path = Path()
                s.values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
                if (series.size == 1) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(x(s.values.lastIndex), bottom)
                        lineTo(x(0), bottom)
                        close()
                    }
                    drawPath(area, s.color.copy(alpha = 0.10f))
                }
                drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = s.values.lastIndex
                endDot(Offset(x(last), y(s.values[last])), s.color, colors.surface)
                if (markLowest) {
                    val minIndex = s.values.indices.minBy { s.values[it] }
                    if (minIndex != last) endDot(Offset(x(minIndex), y(s.values[minIndex])), s.color, colors.surface)
                }
            }

            touchX?.let { tx ->
                val index = (((tx - left) / plotWidth) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                val cx = x(index)
                drawLine(colors.baseline, Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx())
                series.forEach { s -> s.values.getOrNull(index)?.let { endDot(Offset(cx, y(it)), s.color, colors.surface) } }
                val lines = listOf(xLabel(index)) + series.mapNotNull { s ->
                    s.values.getOrNull(index)?.let { if (series.size > 1) "${s.label}: ${valueLabel(it)}" else valueLabel(it) }
                }
                tooltip(measurer, lines, cx, top, size.width, tooltipBg, tooltipText)
            }
        }
        if (series.size >= 2) Legend(series)
    }
}

private fun DrawScope.endDot(center: Offset, color: Color, ring: Color) {
    drawCircle(ring, radius = 6.dp.toPx(), center = center)
    drawCircle(color, radius = 4.dp.toPx(), center = center)
}

private fun DrawScope.tooltip(measurer: TextMeasurer, lines: List<String>, anchorX: Float, top: Float, width: Float, bg: Color, fg: Color) {
    val layouts = lines.mapIndexed { i, line ->
        measurer.measure(line, TextStyle(fontSize = 12.sp, color = fg, fontWeight = if (i == 0) null else androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
    val pad = 8.dp.toPx()
    val boxWidth = (layouts.maxOf { it.size.width } + pad * 2)
    val boxHeight = layouts.sumOf { it.size.height } + pad * 2
    val boxLeft = (anchorX - boxWidth / 2).coerceIn(0f, width - boxWidth)
    drawRoundRect(bg, topLeft = Offset(boxLeft, top), size = Size(boxWidth, boxHeight.toFloat()), cornerRadius = CornerRadius(8.dp.toPx()))
    var y = top + pad
    layouts.forEach {
        drawText(it, topLeft = Offset(boxLeft + pad, y))
        y += it.size.height
    }
}

/** Tiny trend line for stat tiles: one 2dp line and an end dot, no axes. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = LocalChartColors.current.series1) {
    if (values.size < 2) return
    val ring = LocalChartColors.current.surface
    Canvas(modifier) {
        val lo = values.min()
        val hi = values.max()
        val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        val pad = 5.dp.toPx()
        fun x(i: Int) = pad + (size.width - pad * 2) * i / (values.size - 1)
        fun y(v: Double) = (size.height - pad - (v - lo) / range * (size.height - pad * 2)).toFloat()
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        endDot(Offset(x(values.lastIndex), y(values.last())), color, ring)
    }
}

data class BarItem(val key: String, val label: String, val value: Long, val trailing: String, val icon: String? = null)

/** Ranked horizontal bars in one hue: magnitude reads from length, the value sits at the end in text color. */
@Composable
fun BarList(items: List<BarItem>, modifier: Modifier = Modifier, onClick: ((BarItem) -> Unit)? = null) {
    val color = LocalChartColors.current.series1
    val max = items.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1
    Column(modifier) {
        items.forEach { item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (onClick != null) Modifier.clickable { onClick(item) } else Modifier)
                    .padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.icon != null) {
                        Text(item.icon, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.trailing, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth((item.value.toFloat() / max).coerceIn(0.02f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(color),
                )
            }
        }
    }
}

data class ColumnGroup(val label: String, val values: List<Double>)

/** Grouped columns (e.g. income vs spending per month) with a legend and tap-for-values. */
@Composable
fun ColumnChart(
    groups: List<ColumnGroup>,
    series: List<Pair<String, Color>>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Int = 180,
    description: String = "",
) {
    if (groups.isEmpty()) return
    val colors = LocalChartColors.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    val measurer = rememberTextMeasurer()
    var selected by remember { mutableStateOf<Int?>(null) }
    val axis = axisFor(groups.flatMap { it.values } + 0.0, includeZero = true)
    val labelStyle = TextStyle(fontSize = 11.sp, color = muted)

    Column(modifier.semantics { if (description.isNotEmpty()) contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .pointerInput(groups) {
                    detectTapGestures { offset ->
                        val slot = size.width / groups.size.toFloat()
                        val index = (offset.x / slot).toInt().coerceIn(0, groups.lastIndex)
                        selected = if (selected == index) null else index
                    }
                },
        ) {
            val tickLabels = axis.ticks.map { measurer.measure(valueLabel(it), labelStyle) }
            val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 18.dp.toPx()
            val right = size.width
            fun y(v: Double) = (bottom - (v - axis.min) / (axis.max - axis.min) * (bottom - top)).toFloat()
            axis.ticks.forEachIndexed { i, tick ->
                val ty = y(tick)
                drawLine(if (tick == 0.0) colors.baseline else colors.grid, Offset(left, ty), Offset(right, ty), strokeWidth = 1f)
                drawText(tickLabels[i], topLeft = Offset(left - tickLabels[i].size.width - 6.dp.toPx(), ty - tickLabels[i].size.height / 2))
            }
            val slot = (right - left) / groups.size
            val gap = 2.dp.toPx()
            val barWidth = minOf(24.dp.toPx(), (slot * 0.7f - gap * (series.size - 1)) / series.size)
            val radius = 4.dp.toPx()
            groups.forEachIndexed { g, group ->
                val groupWidth = barWidth * series.size + gap * (series.size - 1)
                val startX = left + slot * g + (slot - groupWidth) / 2
                group.values.forEachIndexed { s, value ->
                    val bx = startX + s * (barWidth + gap)
                    val zero = y(0.0)
                    val vy = y(value)
                    val topY = minOf(zero, vy)
                    val h = abs(zero - vy)
                    if (h > 0.5f) {
                        val rounded = if (value >= 0) {
                            RoundRect(bx, topY, bx + barWidth, topY + h, topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius))
                        } else {
                            RoundRect(bx, topY, bx + barWidth, topY + h, bottomLeftCornerRadius = CornerRadius(radius), bottomRightCornerRadius = CornerRadius(radius))
                        }
                        drawPath(Path().apply { addRoundRect(rounded) }, series.getOrNull(s)?.second ?: colors.series1)
                    }
                }
                val text = measurer.measure(group.label, labelStyle)
                drawText(text, topLeft = Offset(left + slot * g + (slot - text.size.width) / 2, bottom + 4.dp.toPx()))
            }
            selected?.let { g ->
                val group = groups[g]
                val lines = listOf(group.label) + group.values.mapIndexed { s, v -> "${series.getOrNull(s)?.first}: ${valueLabel(v)}" }
                tooltip(measurer, lines, left + slot * g + slot / 2, top, size.width, tooltipBg, tooltipText)
            }
        }
        if (series.size >= 2) Legend(series.map { ChartSeries(it.first, emptyList(), it.second) })
    }
}

/** Legend swatch helper for places that label series outside a chart. */
@Composable
fun SeriesKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
