package app.agua.ui

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.agua.BuildConfig
import app.agua.data.Fmt
import app.agua.data.Prefs
import app.agua.data.Settings
import app.agua.data.WaterRepo
import app.agua.update.UpdateController
import kotlin.math.roundToInt

private const val ML_PER_KG_SUGGESTION = 35

@Composable
fun SettingsScreen(updates: UpdateController) {
    val ctx = LocalContext.current
    val initial = remember { Prefs.load(ctx) }

    var goal by rememberSaveable { mutableStateOf(initial.goalMl.toString()) }
    var start by rememberSaveable { mutableIntStateOf(initial.startMin) }
    var end by rememberSaveable { mutableIntStateOf(initial.endMin) }
    var weight by rememberSaveable {
        mutableStateOf(if (initial.weightKg > 0) Fmt.kg(initial.weightKg) else "")
    }

    val weightValue = weight.replace(',', '.').toDoubleOrNull()
    val goalValue = goal.toIntOrNull()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = goal,
            onValueChange = { goal = it.filter(Char::isDigit).take(5) },
            label = { Text("Meta diária (mL)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        TimeRow("Início do dia", start) { start = it }
        TimeRow("Fim do dia", end) { end = it }

        OutlinedTextField(
            value = weight,
            onValueChange = { v -> weight = v.filter { it.isDigit() || it == ',' || it == '.' }.take(6) },
            label = { Text("Peso (kg)") },
            supportingText = { Text("Usado para mostrar quantos mL/kg você está tomando") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        if (weightValue != null && weightValue in 20.0..400.0) {
            val suggested = ((weightValue * ML_PER_KG_SUGGESTION) / 50).roundToInt() * 50
            TextButton(onClick = { goal = suggested.toString() }) {
                Text("Usar meta sugerida: $suggested mL ($ML_PER_KG_SUGGESTION mL/kg)")
            }
        }

        Button(
            onClick = {
                val g = goalValue
                val w = if (weight.isBlank()) 0.0 else weightValue
                when {
                    g == null || g !in 100..20000 ->
                        toast(ctx, "Meta deve estar entre 100 e 20000 mL")
                    w == null || (w != 0.0 && w !in 20.0..400.0) ->
                        toast(ctx, "Peso inválido")
                    else -> {
                        WaterRepo.saveSettings(ctx, Settings(g, start, end, w))
                        toast(ctx, "Ajustes salvos")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salvar") }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Como funciona", style = MaterialTheme.typography.titleSmall)
                val mins = run {
                    val d = ((end - start) % 1440 + 1440) % 1440
                    if (d == 0) 1440 else d
                }
                Text(
                    "Sua meta é distribuída igualmente entre ${Fmt.time(start)} e ${Fmt.time(end)} " +
                        "(${mins / 60}h${if (mins % 60 > 0) " ${mins % 60}min" else ""}). " +
                        "O status mostra a diferença entre o que você tomou e o que deveria ter tomado até agora.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "O dia vira no meio do seu período de descanso, então um copo depois da meia-noite " +
                        "ainda conta para o dia anterior.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Widget: pressione e segure na tela inicial → Widgets → Água.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Versão ${BuildConfig.VERSION_NAME}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(
                    onClick = { updates.check(manual = true) },
                    enabled = !updates.checking,
                ) { Text(if (updates.checking) "Verificando…" else "Verificar atualização") }
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = {
            TimePickerDialog(
                ctx,
                { _, h, m -> onChange(h * 60 + m) },
                minutes / 60,
                minutes % 60,
                true,
            ).show()
        }) {
            Text(Fmt.time(minutes), fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun toast(ctx: android.content.Context, msg: String) =
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
