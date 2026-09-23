package app.ninho.android

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One atomic snapshot includes both session insertion and timer reset, preventing duplicate sessions. */
class StudyStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "study-v1.json"))
    private val backups = DailyStudyBackups(File(context.noBackupFilesDir, "daily-study-backups"), File(context.filesDir, "compact-backup/latest.ninho-backup.gz"))
    var backupNotice: String? = null; private set
    var lastBackupAt: Long? = null; private set
    fun load(): StudyData = synchronized(dataLock) {
        val bytes = try { file.readFully() } catch (_: java.io.FileNotFoundException) {
            // Existing but unreadable/corrupt canonical state must never be overwritten automatically.
            val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists() || File(file.baseFile.path + ".new").exists()
            check(!exists) { "Os dados atuais existem e precisam ser preservados." }
            val recovered = backups.restoreIfEmpty(exists)
            if (recovered != null) {
                writeCanonical(recovered.data)
                backupNotice = "Seu perfil e seus estudos foram recuperados do backup leve. O temporizador voltou pausado."
                lastBackupAt = recovered.capturedAt
                return recovered.data
            }
            if (backups.hasBackupFiles()) backupNotice = "Há uma cópia que não pôde ser recuperada. Ela foi preservada; você pode importar outro backup no Perfil."
            return StudyData()
        }
        val decoded = decode(String(bytes, Charsets.UTF_8))
        refreshBackup(decoded)
        return decoded.copy(usage = UsageLedger.prune(decoded.usage, System.currentTimeMillis()))
    }
    fun save(data: StudyData) = synchronized(dataLock) {
        writeCanonical(data)
    }
    fun refreshBackupFromDisk() = synchronized(dataLock) { load() }
    fun exportBackup(data: StudyData): ByteArray = synchronized(dataLock) { StudyBackupCodec.encode(pausedForBackup(data), System.currentTimeMillis()) }
    fun restoreBackup(data: StudyData) = synchronized(dataLock) {
        // A failed safety copy stops an explicit replacement before canonical data is touched.
        val current = load()
        backups.preserveBeforeRestore(pausedForBackup(current), System.currentTimeMillis())
        writeCanonical(data)
        try {
            backups.publishRestored(data, System.currentTimeMillis())
            lastBackupAt = backups.latestDate()
            backupNotice = "Backup restaurado. O temporizador está pausado; o modelo de IA deve ser importado separadamente."
        } catch (_: Exception) { backupNotice = "Seus estudos foram restaurados, mas a cópia automática ainda precisa ser atualizada." }
    }
    private fun pausedForBackup(data: StudyData) = data.copy(timer = data.timer.paused(android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), TimerReminder.bootCount(context)))
    private fun refreshBackup(data: StudyData) {
        try {
            if (backups.updateIfDue(pausedForBackup(data), System.currentTimeMillis())) {
                lastBackupAt = backups.latestDate()
                backupNotice = null
            } else if (lastBackupAt == null) lastBackupAt = backups.latestDate()
        } catch (_: Exception) { backupNotice = "Seus estudos estão salvos. Não foi possível atualizar o backup leve; verifique o espaço disponível." }
    }
    private fun writeCanonical(data: StudyData) {
        val stream = file.startWrite()
        try { stream.write(encode(data).toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    companion object {
        // Widgets and alarm receivers share this process and must not race AtomicFile readers/writers.
        private val dataLock = Any()
        fun encode(data: StudyData): String = JSONObject().apply {
            put("version", 3)
            put("profile", profileJson(data.profile))
            put("settings", JSONObject().put("theme", data.settings.theme).put("reducedMotion", data.settings.reducedMotion).put("timerReminder", data.settings.timerReminder).put("appUsage", data.settings.appUsage))
            put("usage", JSONArray(data.usage.map { JSONObject().put("day", it.day).put("route", it.route).put("activeSeconds", it.activeSeconds).put("visits", it.visits).put("timerPauses", it.timerPauses) }))
            put("tutorialsSeen", JSONArray(data.tutorialsSeen.toList()))
            put("subjects", JSONArray(data.subjects.map { JSONObject().put("id", it.id).put("name", it.name) }))
            put("sessions", JSONArray(data.sessions.map { JSONObject().put("id", it.id).put("subjectId", it.subjectId).put("seconds", it.seconds).put("at", it.at).put("rating", it.rating).put("note", it.note) }))
            put("questions", JSONArray(data.questions.map { JSONObject().put("id", it.id).put("subjectId", it.subjectId).put("prompt", it.prompt).put("answer", it.answer) }))
            put("attempts", JSONArray(data.attempts.map { JSONObject().put("questionId", it.questionId).put("subjectId", it.subjectId).put("correct", it.correct).put("at", it.at).put("response", it.response) }))
            put("timer", JSONObject().apply {
                put("subjectId", data.timer.subjectId); put("targetSeconds", data.timer.targetSeconds); put("stopwatch", data.timer.stopwatch)
                put("elapsedMillis", data.timer.elapsedMillis); put("running", data.timer.running)
                put("startedMonotonic", data.timer.startedMonotonic); put("startedWall", data.timer.startedWall)
                put("bootCount", data.timer.bootCount)
            })
        }.toString()
        fun decode(text: String): StudyData {
            val root = JSONObject(text)
            require(root.getInt("version") in 1..3) { "Formato de dados não reconhecido." }
            fun <T> items(name: String, read: (JSONObject) -> T): List<T> = root.getJSONArray(name).let { array -> (0 until array.length()).map { read(array.getJSONObject(it)) } }
            val timer = root.getJSONObject("timer")
            return StudyData(
                subjects = items("subjects") { Subject(it.getString("id"), it.getString("name")) },
                sessions = items("sessions") { StudySession(it.getString("id"), it.getString("subjectId"), it.getLong("seconds"), it.getLong("at"), it.getInt("rating"), it.getString("note")) },
                questions = items("questions") { Question(it.getString("id"), it.getString("subjectId"), it.getString("prompt"), it.getString("answer")) },
                attempts = items("attempts") { Attempt(it.getString("questionId"), it.getString("subjectId"), it.getBoolean("correct"), it.getLong("at"), it.optString("response")) },
                timer = FocusTimer(timer.getString("subjectId"), timer.getLong("targetSeconds"), timer.getBoolean("stopwatch"), timer.getLong("elapsedMillis"), timer.getBoolean("running"), timer.getLong("startedMonotonic"), timer.getLong("startedWall"), timer.optInt("bootCount", -1)),
                profile = root.optJSONObject("profile")?.let(::readProfile) ?: StudentProfile(),
                settings = root.optJSONObject("settings")?.let { UserSettings(it.optString("theme", "system").takeIf { t -> t in listOf("system", "light", "dark") } ?: "system", it.optBoolean("reducedMotion"), it.optBoolean("timerReminder"), it.optBoolean("appUsage", true)) } ?: UserSettings(),
                usage = root.optJSONArray("usage")?.let { a -> (0 until minOf(a.length(), 10000)).map { a.getJSONObject(it) }.map { AppUsageDay(it.optString("day"), it.optString("route"), it.optLong("activeSeconds"), it.optInt("visits"), it.optInt("timerPauses")) } } ?: emptyList(),
                tutorialsSeen = root.optJSONArray("tutorialsSeen")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it in ScreenTutorials.routes }.toSet() } ?: emptySet(),
            )
        }

        /** Personal facts only. Generated plans never become system instructions or substitute for facts. */
        fun profileFacts(profile: StudentProfile): JSONObject = profile.normalized().let { p -> JSONObject()
            .put("name", p.name).put("goal", p.goal).put("motivation", p.motivation).put("targetDate", p.targetDate)
            .put("subjects", p.subjects).put("level", p.level).put("routine", p.routine)
            .put("availableDays", JSONArray(p.availableDays)).put("preferredTime", p.preferredTime)
            .put("dailyMinutes", p.dailyMinutes).put("sessionMinutes", p.sessionMinutes)
            .put("challenges", p.challenges).put("preferences", p.preferences).put("accessibility", p.accessibility)
            .put("revision", p.revision)
        }
        private fun profileJson(p: StudentProfile) = profileFacts(p).apply {
            put("completedAt", p.completedAt ?: JSONObject.NULL); put("updatedAt", p.updatedAt ?: JSONObject.NULL)
            put("plan", p.plan); put("planStatus", p.planStatus); put("planProfileRevision", p.planProfileRevision)
        }
        private fun readProfile(p: JSONObject) = StudentProfile(
            name = p.optString("name"), goal = p.optString("goal"), motivation = p.optString("motivation"), targetDate = p.optString("targetDate"),
            subjects = p.optString("subjects"), level = p.optString("level"), routine = p.optString("routine"),
            availableDays = p.optJSONArray("availableDays")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
            preferredTime = p.optString("preferredTime"), dailyMinutes = p.optInt("dailyMinutes", 60), sessionMinutes = p.optInt("sessionMinutes", 25),
            challenges = p.optString("challenges"), preferences = p.optString("preferences"), accessibility = p.optString("accessibility"),
            completedAt = if (p.isNull("completedAt")) null else p.optLong("completedAt"), updatedAt = if (p.isNull("updatedAt")) null else p.optLong("updatedAt"),
            revision = p.optInt("revision"), plan = p.optString("plan"), planStatus = p.optString("planStatus", "none"), planProfileRevision = p.optInt("planProfileRevision"),
        ).normalized()
    }
}
