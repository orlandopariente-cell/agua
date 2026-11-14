package app.agua.ui

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.agua.data.Entry
import app.agua.data.Fmt
import app.agua.data.Prefs
import app.agua.data.Status
import app.agua.data.WaterRepo
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val QUICK_AMOUNTS = listOf(100, 180, 500, 700)

/** Current time, refreshed periodically so "expected" keeps moving while the app is open. */
@Composable
fun rememberNow(periodMs: Long = 30_000): State<LocalDateTime> =
    produceState(LocalDateTime.now()) {
        while (true) {
            delay(periodMs)
            value = LocalDateTime.now()
        }
    }

@Composable
fun TodayScreen() {
    val ctx = LocalContext.current
    val version by WaterRepo.changes.collectAsState()
    val now by rememberNow()
    val settings = remember(version) { Prefs.load(ctx) }
    val status = remember(version, now) { WaterRepo.status(ctx, now) }
    val entries = remember(version, status.day) { WaterRepo.entriesFor(ctx, status.day) }
    var custom by rememberSaveable { mutableStateOf("") }

    fun addCustom() {
        val ml = custom.toIntOrNull()
        if (ml != null && ml in 1..5000) {
            WaterRepo.add(ctx, ml)
            custom = ""
        } else {
            Toast.makeText(ctx, "Informe um valor entre 1 e 5000 mL", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            StatusCard(status, "${Fmt.time(settings.startMin)} – ${Fmt.time(settings.endMin)}")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_AMOUNTS.forEach { ml ->
                    FilledTonalButton(
                        onClick = { WaterRepo.add(ctx, ml) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    ) { Text("+$ml") }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    label = { Text("Outro valor (mL)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { addCustom() }),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { addCustom() }) { Text("Adicionar") }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    val removed = WaterRepo.undoLast(ctx)
                    val msg = if (removed != null) "Desfeito: -$removed mL" else "Nada para desfazer hoje"
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("↶  Desfazer último") }
        }

        item {
            Text(
                "Registros de hoje",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "Nenhum registro ainda.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(entries, key = { it.id }) { e ->
            EntryRow(e) { WaterRepo.delete(ctx, e.id) }
        }
    }
}

@Composable
private fun StatusCard(st: Status, window: String) {
    val diffColor = if (st.diffMl >= 0) StatusOk else StatusLow
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Status agora", style = MaterialTheme.typography.labelLarge)
            Text(
                Fmt.signedMl(st.diffMl),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = diffColor,
            )
            Text(
                when {
                    st.diffMl > 0 -> "acima do esperado para este horário"
                    st.diffMl < 0 -> "abaixo do esperado para este horário"
                    else -> "exatamente no ritmo"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            WaterBar(st.fraction.toFloat(), st.expectedFraction.toFloat())
            Spacer(Modifier.height(4.dp))
            Text(
                "A marca na barra indica o esperado agora",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            val pct = (st.fraction * 100).toInt()
            InfoLine("Tomado", "${st.consumedMl} de ${st.goalMl} mL ($pct%)")
            InfoLine("Esperado até agora", "${st.expectedMl} mL")
            InfoLine("Por peso", st.mlPerKg?.let { Fmt.mlPerKg(it) } ?: "defina o peso em Ajustes")
            InfoLine("Janela do dia", window)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WaterBar(consumed: Float, expected: Float, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
    val mark = MaterialTheme.colorScheme.onPrimaryContainer
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, size = size, cornerRadius = r)
        val c = consumed.coerceIn(0f, 1f)
        if (c > 0f) {
            drawRoundRect(fill, size = Size(size.width * c, size.height), cornerRadius = r)
        }
        val x = size.width * expected.coerceIn(0f, 1f)
        drawLine(
            mark,
            Offset(x, -4.dp.toPx()),
            Offset(x, size.height + 4.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun EntryRow(e: Entry, onDelete: () -> Unit) {
    val time = Instant.ofEpochMilli(e.timeMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(time.format(TIME_FMT), Modifier.weight(1f))
            Text("${e.ml} mL", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Apagar")
            }
        }
        HorizontalDivider()
    }
}
