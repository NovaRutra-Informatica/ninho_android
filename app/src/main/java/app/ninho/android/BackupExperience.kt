package app.ninho.android

import android.content.Intent
import android.content.ActivityNotFoundException
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

@Composable internal fun BackupControls(model: NinhoModel, firstRun: Boolean = false) {
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri -> uri?.let(model::exportBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::inspectBackup) }
    fun openBackup() = restore.launch(arrayOf("application/gzip", "application/x-gzip", "application/octet-stream"))
    if (firstRun) {
        TextButton(onClick = ::openBackup, enabled = !model.backupBusy && !model.saving) { Text("Já usa o Ninho? Restaurar backup") }
        model.backupNotice?.let { Text(it, fontSize = 12.sp, lineHeight = 19.sp) }
    } else Panel {
        Text("Seu caminho está guardado", fontFamily = NinhoDisplayFont, fontSize = 25.sp)
        Text("Uma cópia leve por dia, atualizada conforme você estuda. Mantemos os últimos sete dias neste aparelho.", lineHeight = 22.sp)
        model.lastBackupAt?.let { Text("Última cópia local: ${backupDate(it)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
        Text("Inclui perfil, matérias, sessões, questões, respostas, preferências e plano salvo da assistente. Não inclui arquivos ou modelos de IA.", fontSize = 13.sp, lineHeight = 21.sp)
        Text("Com o backup do Android ativado, o sistema pode guardar a cópia leve e recuperá-la em uma nova instalação. O horário e o envio dependem do Android e da sua conta.", fontSize = 13.sp, lineHeight = 21.sp)
        TextButton(onClick = {
            try { context.startActivity(Intent("android.settings.BACKUP_SETTINGS")) }
            catch (_: ActivityNotFoundException) { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }) { Text("Backup do Android") }
        OutlinedButton(onClick = { export.launch("Ninho-${LocalDate.now()}.${StudyBackupCodec.EXTENSION}") }, enabled = !model.backupBusy && !model.saving) { Text("Exportar backup leve") }
        OutlinedButton(onClick = ::openBackup, enabled = !model.backupBusy && !model.saving) { Text("Restaurar um backup") }
        Text("Escolha uma pasta em Arquivos ou um provedor como o Drive ao exportar. O arquivo contém seus dados pessoais; guarde-o em um local seu. Para restaurar, selecione um .ninho-backup.gz do Ninho Android, de até 2 MB.", fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.backupNotice?.let { Text(it, fontSize = 13.sp, lineHeight = 21.sp) }
    }
    model.pendingBackup?.let { backup ->
        AlertDialog(onDismissRequest = model::cancelBackupRestore,
            title = { Text("Restaurar este caminho?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cópia de ${backupDate(backup.capturedAt)}", fontWeight = FontWeight.SemiBold)
                Text("${backup.data.subjects.size} matérias · ${backup.data.sessions.size} sessões · ${backup.data.questions.size} questões")
                Text("Substitui o perfil e os estudos atuais pelos dados desta cópia. Antes, guardaremos uma cópia de segurança local. O temporizador volta pausado e modelos de IA não são importados.")
            } },
            confirmButton = { TextButton(onClick = model::restoreInspectedBackup, enabled = !model.backupBusy && !model.saving) { Text("Restaurar backup") } },
            dismissButton = { TextButton(onClick = model::cancelBackupRestore, enabled = !model.backupBusy) { Text("Manter meus dados") } })
    }
}

private fun backupDate(value: Long) = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(value))
