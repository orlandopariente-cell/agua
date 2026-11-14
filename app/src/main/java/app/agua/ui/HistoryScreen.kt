package app.agua.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.agua.data.DaySummary
import app.agua.data.Fmt
import app.agua.data.WaterRepo
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd/MM", Locale.getDefault())

@Composable
fun HistoryScreen() {
    val ctx = LocalContext.current
    val version by WaterRepo.changes.collectAsState()
    val now by rememberNow(60_000)
    val history = remember(version, now.toLocalDate(), now.hour) { WaterRepo.history(ctx, 90) }
    val today = history.lastOrNull()?.day

    // Completed days only (today is still in progress)
    val past = history.filter { it.day != today }
    val last7 = past.takeLast(7)
    val avg7 = if (last7.isNotEmpty()) last7.sumOf { it.totalMl } / last7.size else 0
    val avgKg = last7.mapNotNull { it.mlPerKg }.let { if (it.isNotEmpty()) it.average() else null }
    val last30 = past.takeLast(30)
    val hits30 = last30.count { it.reachedGoal }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Histórico", style = MaterialTheme.typography.headlineSmall) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Últimos 14 dias", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    HistoryChart(history.takeLast(14))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Barras cheias: meta atingida. Traço: meta do dia.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    StatLine("Média dos últimos 7 dias", if (last7.isEmpty()) "—" else "$avg7 mL")
                    StatLine("Média por peso (7 dias)", avgKg?.let { Fmt.mlPerKg(it) } ?: "—")
                    StatLine(
                        "Meta atingida (30 dias)",
                        if (last30.isEmpty()) "—" else "$hits30 de ${last30.size} dias"
                    )
                }
            }
        }

        items(history.reversed(), key = { it.day.toString() }) { d ->
            DayRow(d, isToday = d.day == today)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryChart(days: List<DaySummary>) {
    val primary = MaterialTheme.colorScheme.primary
    val goalColor = MaterialTheme.colorScheme.onSurface
    val maxV = (days.maxOfOrNull { maxOf(it.totalMl, it.goalMl) } ?: 1).coerceAtLeast(1) * 1.1f

    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val n = days.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.6f
            days.forEachIndexed { i, d ->
                val x0 = i * slot
                val h = size.height * d.totalMl / maxV
                if (h > 0f) {
                    drawRoundRect(
                        color = if (d.reachedGoal) primary else primary.copy(alpha = 0.4f),
                        topLeft = Offset(x0 + (slot - barW) / 2, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
                val gy = size.height - size.height * d.goalMl / maxV
                drawLine(
                    goalColor.copy(alpha = 0.6f),
                    Offset(x0 + slot * 0.1f, gy),
                    Offset(x0 + slot * 0.9f, gy),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Text(
                    it.day.dayOfMonth.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DayRow(d: DaySummary, isToday: Boolean) {
    val pct = (d.fraction * 100).roundToInt()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isToday) "Hoje (parcial)" else d.day.format(DAY_FMT),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${d.totalMl} mL",
                    fontWeight = FontWeight.Bold,
                    color = if (d.reachedGoal) StatusOk else MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            WaterBarSimple(d.fraction.toFloat())
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    "$pct% de ${d.goalMl} mL",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                d.mlPerKg?.let {
                    Text(
                        Fmt.mlPerKg(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaterBarSimple(fraction: Float) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, size = size, cornerRadius = r)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) drawRoundRect(fill, size = Size(size.width * f, size.height), cornerRadius = r)
    }
}
