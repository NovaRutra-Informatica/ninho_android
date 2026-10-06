package app.ninho.android

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import java.io.IOException

class StudyBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        try {
            val encrypted = data.transportFlags and FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0
            val transfer = data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER != 0
            if (!encrypted && !transfer) throw IOException("O destino não oferece proteção para esta cópia.")
            fullBackupFile(StudyStore(this).systemBackupFile(), data)
        } catch (error: Exception) {
            throw IOException("Envio adiado para preservar a cópia anterior.", error)
        }
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
        throw IOException("Esta instalação usa somente backup de arquivo completo.")
    }
    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) {
        throw IOException("Restaure o backup de arquivo completo do Ninho.")
    }
}
