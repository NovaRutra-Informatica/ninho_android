package app.ninho.android

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class RestorableStudyBackup(val data: StudyData, val capturedAt: Long)

object StudyBackupCodec {
    const val MAX_COMPRESSED = 2 * 1024 * 1024
    const val MAX_EXPANDED = 8 * 1024 * 1024
    const val EXTENSION = "ninho-backup.gz"

    fun encode(data: StudyData, capturedAt: Long): ByteArray {
        require(capturedAt in 1..253402300799999L)
        validate(data)
        // A restored timer must never start by itself or count time spent on another installation.
        val state = StudyStore.encode(data.copy(timer = data.timer.copy(running = false, startedMonotonic = 0, startedWall = 0, bootCount = -1)))
        val envelope = JSONObject().put("format", "ninho.android.compact-backup").put("version", 1)
            .put("capturedAt", capturedAt).put("sha256", digest(state)).put("state", state).toString().toByteArray(Charsets.UTF_8)
        require(envelope.size <= MAX_EXPANDED) { "Seus registros ultrapassam o limite de 8 MB do backup leve." }
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(envelope) }
        return output.toByteArray().also { require(it.size <= MAX_COMPRESSED) { "O backup compactado ultrapassa 2 MB." } }
    }

    fun decode(bytes: ByteArray): RestorableStudyBackup {
        require(bytes.size in 1..MAX_COMPRESSED) { "Selecione um backup Ninho de até 2 MB." }
        val unpacked = GZIPInputStream(ByteArrayInputStream(bytes)).use { readBounded(it, MAX_EXPANDED) }
        val envelope = JSONObject(unpacked.toString(Charsets.UTF_8))
        require(envelope.getString("format") == "ninho.android.compact-backup" && envelope.getInt("version") == 1) { "Este backup não é compatível com o Ninho Android." }
        val captured = envelope.getLong("capturedAt").also { require(it in 1..253402300799999L) }
        val state = envelope.getString("state")
        require(envelope.getString("sha256") == digest(state)) { "A integridade do backup não pôde ser confirmada." }
        val data = StudyStore.decode(state)
        validate(data)
        return RestorableStudyBackup(data.copy(timer = data.timer.copy(running = false, startedMonotonic = 0, startedWall = 0, bootCount = -1)), captured)
    }

    fun readBounded(input: InputStream, maximum: Int = MAX_COMPRESSED): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maximum + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= maximum) { "O arquivo ultrapassa o limite do backup leve." }
        }
        return output.toByteArray()
    }

    fun hasRecords(data: StudyData) = data != StudyData()
    private fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun validate(data: StudyData) {
        fun text(value: String, maximum: Int = 65536) { require(value.length <= maximum) { "Um campo do backup excede o limite permitido." } }
        fun id(value: String) { require(value.isNotBlank() && value.length <= 512) { "Identificador inválido no backup." } }
        fun time(value: Long) { require(value in 1..253402300799999L) { "Data inválida no backup." } }
        require(data.subjects.size <= 10000 && data.sessions.size <= 100000 && data.questions.size <= 50000 && data.attempts.size <= 100000)
        require(data.subjects.map { it.id }.distinct().size == data.subjects.size && data.sessions.map { it.id }.distinct().size == data.sessions.size && data.questions.map { it.id }.distinct().size == data.questions.size)
        data.subjects.forEach { id(it.id); text(it.name) }
        data.sessions.forEach { id(it.id); id(it.subjectId); require(it.seconds in 0..86400 && it.rating in 1..3); time(it.at); text(it.note) }
        data.questions.forEach { id(it.id); id(it.subjectId); text(it.prompt); text(it.answer) }
        data.attempts.forEach { id(it.questionId); id(it.subjectId); time(it.at); text(it.response) }
        require(data.timer.targetSeconds in 60..14400 && data.timer.elapsedMillis in 0..86400000)
        require(data.timer.stopwatch || data.timer.elapsedMillis <= data.timer.targetSeconds * 1000)
        text(data.timer.subjectId, 512)
        data.profile.completedAt?.let(::time); data.profile.updatedAt?.let(::time)
        require(data.profile.revision >= 0 && data.profile.planProfileRevision >= 0)
        text(data.profile.plan)
    }
}

class DailyStudyBackups(private val archiveDirectory: File, private val latestFile: File) {
    fun updateIfDue(data: StudyData, now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!StudyBackupCodec.hasRecords(data)) return false
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        val archive = File(archiveDirectory, "$day.${StudyBackupCodec.EXTENSION}")
        val previous = if (archive.isFile) try { checkedBytes(archive).let { it to StudyBackupCodec.decode(it) } } catch (_: Exception) { null } else null
        val snapshot = data.copy(timer = data.timer.copy(running = false, startedMonotonic = 0, startedWall = 0, bootCount = -1))
        val unchanged = previous != null && previous.second.data == snapshot
        val bytes = if (unchanged) previous.first else StudyBackupCodec.encode(snapshot, now).also { atomicWrite(archive, it) }
        if (unchanged && latestFile.isFile && latestFile.length() == bytes.size.toLong() && latestFile.inputStream().use { StudyBackupCodec.readBounded(it) }.contentEquals(bytes)) {
            rotate("", 7)
            return false
        }
        atomicWrite(latestFile, bytes)
        rotate("", 7)
        return true
    }

    fun publishRestored(data: StudyData, now: Long) {
        atomicWrite(latestFile, StudyBackupCodec.encode(data, now))
    }

    fun preserveBeforeRestore(data: StudyData, now: Long) {
        if (!StudyBackupCodec.hasRecords(data)) return
        atomicWrite(File(archiveDirectory, "before-restore-$now.${StudyBackupCodec.EXTENSION}"), StudyBackupCodec.encode(data, now))
        rotate("before-restore-", 3)
    }

    fun restoreIfEmpty(canonicalExists: Boolean): RestorableStudyBackup? {
        if (canonicalExists) return null
        val candidates = listOf(latestFile) + archiveDirectory.listFiles().orEmpty()
            .filter { it.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.ninho-backup\\.gz")) }.sortedByDescending { it.name }
        var newest: RestorableStudyBackup? = null
        for (file in candidates) if (file.isFile) {
            try {
                val copy = StudyBackupCodec.decode(checkedBytes(file))
                if (copy.capturedAt > (newest?.capturedAt ?: 0L)) newest = copy
            } catch (_: Exception) { /* Preserve damaged copies and try older valid history. */ }
        }
        return newest
    }

    fun hasBackupFiles() = latestFile.exists() || archiveDirectory.listFiles().orEmpty().isNotEmpty()
    fun latestDate(): Long? = try { StudyBackupCodec.decode(checkedBytes(latestFile)).capturedAt } catch (_: Exception) { null }
    private fun checkedBytes(file: File): ByteArray {
        require(file.length() in 1..StudyBackupCodec.MAX_COMPRESSED.toLong())
        return file.inputStream().use { StudyBackupCodec.readBounded(it) }
    }
    private fun rotate(prefix: String, keep: Int) {
        archiveDirectory.listFiles().orEmpty().filter { file ->
            if (prefix.isEmpty()) file.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\.ninho-backup\\.gz"))
            else file.name.matches(Regex("before-restore-\\d+\\.ninho-backup\\.gz"))
        }.sortedByDescending { it.name }.drop(keep).forEach { check(it.delete()) { "Não foi possível limitar as cópias antigas." } }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        check(target.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val temporary = File(target.parentFile, "${target.name}.pending")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}
