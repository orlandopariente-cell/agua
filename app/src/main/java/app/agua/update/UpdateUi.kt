package app.agua.update

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.agua.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class UpdateController(private val ctx: Context, private val scope: CoroutineScope) {
    var available by mutableStateOf<UpdateInfo?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set

    /** [manual] = user tapped "check": report "up to date" and errors too. */
    fun check(manual: Boolean) {
        if (checking) return
        if (!Updater.isConfigured) {
            if (manual) toast("Atualizações ainda não configuradas nesta versão")
            return
        }
        checking = true
        scope.launch {
            try {
                val u = Updater.check()
                available = u
                if (u == null && manual) toast("Você já tem a versão mais recente (${BuildConfig.VERSION_NAME})")
            } catch (e: Exception) {
                if (manual) toast("Não foi possível verificar: ${e.message ?: "sem conexão"}")
            } finally {
                checking = false
            }
        }
    }

    fun dismiss() {
        available = null
    }

    fun install() {
        val info = available ?: return
        if (!Updater.canInstall(ctx)) {
            toast("Permita \"instalar apps desconhecidos\" para o Água e toque em Atualizar de novo")
            Updater.openInstallPermissionSettings(ctx)
            return
        }
        downloading = true
        scope.launch {
            try {
                val apk = Updater.download(ctx, info)
                Updater.install(ctx, apk)
                available = null
            } catch (e: Exception) {
                toast("Falha no download: ${e.message ?: "erro de rede"}")
            } finally {
                downloading = false
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

@Composable
fun rememberUpdateController(): UpdateController {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateController(ctx, scope) }
}

@Composable
fun UpdateDialog(c: UpdateController) {
    val info = c.available ?: return
    AlertDialog(
        onDismissRequest = { if (!c.downloading) c.dismiss() },
        title = { Text("Atualização disponível") },
        text = {
            Column {
                Text(info.title, fontWeight = FontWeight.SemiBold)
                Text("Você está na ${BuildConfig.VERSION_NAME}")
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text(info.notes)
                    }
                }
                if (c.downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Baixando…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { c.install() }, enabled = !c.downloading) { Text("Atualizar") }
        },
        dismissButton = {
            TextButton(onClick = { c.dismiss() }, enabled = !c.downloading) { Text("Depois") }
        },
    )
}
