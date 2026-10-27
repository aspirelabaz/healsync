package com.aspirelab.healsync.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aspirelab.healsync.R
import com.aspirelab.healsync.data.HealthSummary
import com.aspirelab.healsync.ui.charts.DayBar
import com.aspirelab.healsync.ui.charts.HeartRateSparkline
import com.aspirelab.healsync.ui.charts.ProgressRing
import com.aspirelab.healsync.ui.charts.SleepStageBar
import com.aspirelab.healsync.ui.charts.StageSegment
import com.aspirelab.healsync.ui.charts.WeeklyBarChart
import com.aspirelab.healsync.ui.charts.formatMinutes
import com.aspirelab.healsync.ui.charts.formatNumber
import com.aspirelab.healsync.ui.glass.GlassButton
import com.aspirelab.healsync.ui.glass.GlassCard
import com.aspirelab.healsync.ui.glass.GlassColors
import com.aspirelab.healsync.ui.glass.GlassIconButton
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val STEP_GOAL = 10_000L
private val vietnamese: Locale = Locale.forLanguageTag("vi")
private val dateHeader = DateTimeFormatter.ofPattern("EEEE, d 'tháng' M", vietnamese)
private val clock = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(
    summary: HealthSummary,
    lastSync: Instant?,
    syncing: Boolean,
    healthConnect: HealthConnectState,
    hcGranted: Boolean,
    log: List<String>,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onGrantHealthConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val insets = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 12.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(today, syncing, onSync, onSignOut) }
        item { SyncStatus(lastSync, syncing, zone) }

        if (healthConnect != HealthConnectState.Available || !hcGranted) {
            item { HealthConnectBanner(healthConnect, onGrantHealthConnect, onInstallHealthConnect) }
        }

        item { StepsCard(summary.stepsOn(today)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeartCard(summary, zone, Modifier.weight(1f))
                SleepCard(summary, zone, Modifier.weight(1f))
            }
        }
        summary.lastSleep?.let { item { SleepStagesCard(summary) } }
        item { WeeklyStepsCard(summary, today) }
        item { ActivityCard(log) }
    }
}

@Composable
private fun Header(today: LocalDate, syncing: Boolean, onSync: () -> Unit, onSignOut: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                today.format(dateHeader).replaceFirstChar { it.titlecase(vietnamese) },
                color = GlassColors.TextSecondary,
                fontSize = 14.sp,
            )
            Text("Hôm nay", color = GlassColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        GlassIconButton(onClick = onSync, enabled = !syncing) {
            val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
                initialValue = 0f,
                targetValue = -360f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                label = "rotation",
            )
            Icon(
                painterResource(R.drawable.ic_sync),
                contentDescription = "Đồng bộ ngay",
                tint = GlassColors.TextPrimary,
                modifier = Modifier.size(22.dp).rotate(if (syncing) rotation else 0f),
            )
        }
        Spacer(Modifier.width(10.dp))
        GlassIconButton(onClick = onSignOut) {
            Icon(painterResource(R.drawable.ic_logout), contentDescription = "Đăng xuất", tint = GlassColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SyncStatus(lastSync: Instant?, syncing: Boolean, zone: ZoneId) {
    val text = when {
        syncing -> "Đang đồng bộ từ Fitbit…"
        lastSync == null -> "Chưa đồng bộ lần nào"
        else -> "Đồng bộ lúc ${lastSync.atZone(zone).format(clock)} · ${relative(lastSync)}"
    }
    Text(text, color = GlassColors.TextMuted, fontSize = 13.sp)
}

@Composable
private fun HealthConnectBanner(state: HealthConnectState, onGrant: () -> Unit, onInstall: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = GlassColors.Warning, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                when (state) {
                    HealthConnectState.NotInstalled -> "Máy chưa có Health Connect"
                    HealthConnectState.NeedsUpdate -> "Health Connect cần cập nhật"
                    HealthConnectState.Available -> "Cần quyền ghi vào Health Connect để Samsung Health thấy dữ liệu"
                },
                color = GlassColors.TextPrimary,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        GlassButton(
            onClick = if (state == HealthConnectState.Available) onGrant else onInstall,
            modifier = Modifier.fillMaxWidth(),
            tint = GlassColors.Warning,
        ) {
            Text(
                if (state == HealthConnectState.Available) "Cấp quyền" else "Mở Play Store",
                color = GlassColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StepsCard(steps: Long) {
    GlassCard(Modifier.fillMaxWidth()) {
        CardTitle(painterResource(R.drawable.ic_walk), "Bước chân", GlassColors.Steps)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = steps.toFloat() / STEP_GOAL, color = GlassColors.Steps, modifier = Modifier.size(150.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatNumber(steps), color = GlassColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("bước", color = GlassColors.TextSecondary, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Stat("Mục tiêu", formatNumber(STEP_GOAL))
                Stat("Đã đạt", "${(steps * 100 / STEP_GOAL)}%")
                Stat("Còn lại", formatNumber((STEP_GOAL - steps).coerceAtLeast(0)))
            }
        }
    }
}

@Composable
private fun HeartCard(summary: HealthSummary, zone: ZoneId, modifier: Modifier) {
    GlassCard(modifier.height(210.dp)) {
        CardTitle(rememberVectorPainter(Icons.Filled.Favorite), "Nhịp tim", GlassColors.Heart)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(summary.latestHeartRate?.toString() ?: "--", color = GlassColors.TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text("bpm", color = GlassColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(
            summary.latestHeartRateAt?.let { "lúc " + Instant.ofEpochMilli(it).atZone(zone).format(clock) } ?: "chưa có dữ liệu",
            color = GlassColors.TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        val hourly = summary.heartRateByHour
        HeartRateSparkline(hourly.map { it.avg }, Modifier.fillMaxWidth().height(44.dp))
        if (hourly.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${hourly.minOf { it.min }}–${hourly.maxOf { it.max }} bpm hôm nay",
                color = GlassColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SleepCard(summary: HealthSummary, zone: ZoneId, modifier: Modifier) {
    val sleep = summary.lastSleep
    GlassCard(modifier.height(210.dp)) {
        CardTitle(painterResource(R.drawable.ic_moon), "Giấc ngủ", GlassColors.Sleep)
        Spacer(Modifier.height(12.dp))
        Text(
            sleep?.let { formatMinutes(it.asleepMinutes) } ?: "--",
            color = GlassColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("ngủ đêm qua", color = GlassColors.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        if (sleep != null) {
            Stat("Đi ngủ", Instant.ofEpochMilli(sleep.start).atZone(zone).format(clock))
            Spacer(Modifier.height(8.dp))
            Stat("Thức dậy", Instant.ofEpochMilli(sleep.end).atZone(zone).format(clock))
        }
    }
}

@Composable
private fun SleepStagesCard(summary: HealthSummary) {
    val sleep = summary.lastSleep ?: return
    GlassCard(Modifier.fillMaxWidth()) {
        CardTitle(painterResource(R.drawable.ic_moon), "Giai đoạn ngủ", GlassColors.Sleep)
        Spacer(Modifier.height(16.dp))
        val segments = buildList {
            add(StageSegment("Thức", sleep.awakeMinutes, GlassColors.StageAwake))
            add(StageSegment("REM", sleep.remMinutes, GlassColors.StageRem))
            add(StageSegment("Nông", sleep.lightMinutes, GlassColors.StageLight))
            add(StageSegment("Sâu", sleep.deepMinutes, GlassColors.StageDeep))
            if (sleep.otherMinutes > 0) add(StageSegment("Ngủ", sleep.otherMinutes, GlassColors.TextMuted))
        }
        SleepStageBar(segments)
    }
}

@Composable
private fun WeeklyStepsCard(summary: HealthSummary, today: LocalDate) {
    GlassCard(Modifier.fillMaxWidth()) {
        CardTitle(painterResource(R.drawable.ic_walk), "Bước chân 7 ngày", GlassColors.Steps)
        Spacer(Modifier.height(4.dp))
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val average = days.sumOf { summary.stepsOn(it) } / 7
        Text("Trung bình ${formatNumber(average)} bước/ngày · nét đứt là mục tiêu", color = GlassColors.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        WeeklyBarChart(
            bars = days.map { DayBar(if (it == today) "Nay" else dayLabel(it.dayOfWeek), summary.stepsOn(it)) },
            goal = STEP_GOAL,
            color = GlassColors.Steps,
        )
    }
}

@Composable
private fun ActivityCard(log: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hoạt động đồng bộ", color = GlassColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (expanded) "Thu gọn" else "Xem", color = GlassColors.TextSecondary, fontSize = 13.sp)
        }
        log.firstOrNull()?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = GlassColors.TextSecondary, fontSize = 13.sp)
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                log.drop(1).take(20).forEach { Text(it, color = GlassColors.TextMuted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun CardTitle(icon: Painter, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = GlassColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = GlassColors.TextMuted, fontSize = 12.sp)
        Text(value, color = GlassColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun dayLabel(day: DayOfWeek): String = if (day == DayOfWeek.SUNDAY) "CN" else "T${day.value + 1}"

private fun relative(instant: Instant): String {
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "vừa xong"
        minutes < 60 -> "$minutes phút trước"
        minutes < 24 * 60 -> "${minutes / 60} giờ trước"
        else -> "${minutes / (24 * 60)} ngày trước"
    }
}
