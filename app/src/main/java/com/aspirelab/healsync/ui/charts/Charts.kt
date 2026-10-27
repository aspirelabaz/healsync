package com.aspirelab.healsync.ui.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspirelab.healsync.ui.glass.GlassColors
import java.text.NumberFormat
import java.util.Locale

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi"))
fun formatNumber(value: Long): String = numberFormat.format(value)

/** Progress ring toward a daily goal; the arc animates in when the value changes. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(Color.White.copy(alpha = 0.10f), -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color.copy(alpha = 0.55f))),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** Hourly average heart rate for today as a 2dp line with a soft fill; the latest point is marked. */
@Composable
fun HeartRateSparkline(values: List<Int>, modifier: Modifier = Modifier, color: Color = GlassColors.Heart) {
    Canvas(modifier.semantics { contentDescription = "Nhịp tim theo giờ hôm nay" }) {
        if (values.size < 2) return@Canvas
        val min = values.min() - 5f
        val max = values.max() + 5f
        val stepX = size.width / (values.size - 1)
        fun point(i: Int) = Offset(i * stepX, size.height - (values[i] - min) / (max - min) * size.height)
        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = point(values.lastIndex)
        drawCircle(GlassColors.Base, radius = 6.dp.toPx(), center = last)
        drawCircle(color, radius = 4.dp.toPx(), center = last)
    }
}

data class StageSegment(val label: String, val minutes: Int, val color: Color)

/** Sleep stages as one stacked bar (2dp gaps) plus a legend that names each stage and its duration. */
@Composable
fun SleepStageBar(segments: List<StageSegment>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.minutes }.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val gap = 2.dp.toPx()
            val visible = segments.filter { it.minutes > 0 }
            val usable = size.width - gap * (visible.size - 1).coerceAtLeast(0)
            var x = 0f
            visible.forEach { segment ->
                val w = usable * segment.minutes / total
                drawRoundRect(segment.color, Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
                x += w + gap
            }
        }
        segments.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { segment ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(segment.color, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(segment.label, color = GlassColors.TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(formatMinutes(segment.minutes), color = GlassColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

data class DayBar(val label: String, val value: Long)

/**
 * Seven daily bars anchored to the baseline with a dashed goal line. Tapping a bar selects it and
 * shows its value; today (the last bar) is selected by default.
 */
@Composable
fun WeeklyBarChart(
    bars: List<DayBar>,
    goal: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var selected by remember(bars) { mutableIntStateOf(bars.lastIndex) }
    val maxValue = maxOf(bars.maxOfOrNull { it.value } ?: 0L, goal).toFloat() * 1.1f
    Column(modifier) {
        val current = bars.getOrNull(selected)
        Text(
            text = current?.let { "${it.label}: ${formatNumber(it.value)} bước" } ?: "",
            color = GlassColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val slot = size.width / bars.size.coerceAtLeast(1)
                        selected = (offset.x / slot).toInt().coerceIn(0, bars.lastIndex)
                    }
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val barWidth = minOf(slot * 0.5f, 22.dp.toPx())
            val goalY = size.height - goal / maxValue * size.height
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
            drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val h = (bar.value / maxValue * size.height).coerceAtLeast(if (bar.value > 0) 4.dp.toPx() else 0f)
                val x = slot * i + (slot - barWidth) / 2
                val radius = CornerRadius(4.dp.toPx())
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = x, top = size.height - h, right = x + barWidth, bottom = size.height,
                            topLeftCornerRadius = radius, topRightCornerRadius = radius,
                        ),
                    )
                }
                drawPath(path, if (i == selected) color else color.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            bars.forEachIndexed { i, bar ->
                Text(
                    bar.label,
                    modifier = Modifier.weight(1f),
                    color = if (i == selected) GlassColors.TextPrimary else GlassColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}p"
        m == 0 -> "${h}g"
        else -> "${h}g ${m}p"
    }
}
