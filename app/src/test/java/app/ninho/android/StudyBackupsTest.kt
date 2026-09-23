package app.ninho.android

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class StudyBackupsTest {
    @get:Rule val folder = TemporaryFolder()
    private val now = Instant.parse("2026-09-23T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("UTC")
    private val sample get() = StudyData(
        subjects = listOf(Subject("s", "História")),
        sessions = listOf(StudySession("session", "s", 600, now, 2, "Notas da aula")),
        questions = listOf(Question("q", "s", "Quando?", "Hoje")),
        attempts = listOf(Attempt("q", "s", false, now, "Minha resposta")),
        profile = StudentProfile(name = "Estudante", goal = "Aprender", completedAt = now, plan = "Plano salvo", planStatus = "ready", revision = 2, planProfileRevision = 2),
        settings = UserSettings(theme = "dark"), tutorialsSeen = setOf("today"),
        usage = listOf(AppUsageDay("2026-09-23", "today", 30, 2, 0)))
    private fun repo(root: File = folder.root) = DailyStudyBackups(File(root, "daily"), File(root, "cloud/latest.ninho-backup.gz"))
    private fun envelope(bytes: ByteArray) = JSONObject(GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8))
    private fun gzip(text: String) = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
    private fun changedState(bytes: ByteArray, transform: (JSONObject) -> Unit): ByteArray {
        val root = envelope(bytes)
        val state = JSONObject(root.getString("state"))
        transform(state)
        val text = state.toString()
        root.put("state", text).put("sha256", MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) })
        return gzip(root.toString())
    }
    private fun rejected(action: () -> Unit) { try { action(); fail("Expected invalid backup to be rejected") } catch (error: Exception) { assertTrue(error.message.orEmpty().isNotEmpty()) } }

    @Test fun roundTripKeepsCanonicalRecordsAndNeverImportsPathsOrWeights() {
        val bytes = StudyBackupCodec.encode(sample, now)
        assertEquals(sample, StudyBackupCodec.decode(bytes).data)
        assertEquals(now, StudyBackupCodec.decode(bytes).capturedAt)
        val state = JSONObject(envelope(bytes).getString("state"))
        assertFalse(state.has("model")); assertFalse(state.has("attachments")); assertFalse(state.has("files"))
        assertTrue(bytes.size < 2048)
    }

    @Test fun restoredTimerIsPausedAndCannotAccrueTimeOnAnotherDevice() {
        val timer = FocusTimer(subjectId = "s", running = true, elapsedMillis = 123000, startedWall = now - 1000, startedMonotonic = 9000, bootCount = 8)
        val restored = StudyBackupCodec.decode(StudyBackupCodec.encode(sample.copy(timer = timer), now)).data.timer
        assertFalse(restored.running)
        assertEquals(123000, restored.elapsed(9999999, now + 86400000, 99))
        assertEquals(0, restored.startedMonotonic); assertEquals(-1, restored.bootCount)
    }

    @Test fun existingCanonicalStatePreventsAnyAutomaticRestore() {
        val repo = repo()
        repo.updateIfDue(sample, now, zone)
        assertNull(repo.restoreIfEmpty(canonicalExists = true))
        assertEquals(sample, repo.restoreIfEmpty(canonicalExists = false)?.data)
    }

    @Test fun sameDayUpdatesAfterChangesButIsIdempotentWhenNothingChanged() {
        val repo = repo()
        assertTrue(repo.updateIfDue(sample, now, zone))
        assertFalse(repo.updateIfDue(sample, now + 1000, zone))
        val changed = sample.copy(profile = sample.profile.copy(goal = "Novo objetivo"))
        assertTrue(repo.updateIfDue(changed, now + 2000, zone))
        assertEquals(1, File(folder.root, "daily").listFiles()!!.size)
        assertEquals(changed, repo.restoreIfEmpty(false)?.data)
    }

    @Test fun dailyRotationKeepsSevenDaysAndThreeIndependentSafetyCopies() {
        val repo = repo()
        repeat(12) { repo.updateIfDue(sample, now + it * 86400000L, zone) }
        val unrelated = File(folder.root, "daily/user-note.txt").apply { writeText("Keep") }
        repeat(5) { repo.preserveBeforeRestore(sample, now + it) }
        val files = File(folder.root, "daily").listFiles()!!
        assertEquals(7, files.count { it.name.first().isDigit() })
        assertEquals(3, files.count { it.name.startsWith("before-restore-") })
        assertEquals("Keep", unrelated.readText())
        assertEquals(now + 11 * 86400000L, repo.restoreIfEmpty(false)?.capturedAt)
    }

    @Test fun damagedLatestFallsBackToValidDailyHistoryWithoutDeletingCorruption() {
        val repo = repo()
        repo.updateIfDue(sample, now, zone)
        val latest = File(folder.root, "cloud/latest.ninho-backup.gz").apply { writeText("broken") }
        assertEquals(sample, repo.restoreIfEmpty(false)?.data)
        assertEquals("broken", latest.readText())
    }

    @Test fun interruptedPublicationKeepsOldLatestAndRetryPublishesNewArchive() {
        val repo = repo()
        repo.updateIfDue(sample, now, zone)
        val latest = File(folder.root, "cloud/latest.ninho-backup.gz")
        val oldBytes = latest.readBytes()
        File(latest.parentFile, latest.name + ".pending").mkdir()
        val changed = sample.copy(profile = sample.profile.copy(goal = "Updated"))
        rejected { repo.updateIfDue(changed, now + 1000, zone) }
        assertArrayEquals(oldBytes, latest.readBytes())
        assertEquals(changed, repo.restoreIfEmpty(false)?.data)
        assertTrue(repo.updateIfDue(changed, now + 2000, zone))
        assertEquals(changed, repo.restoreIfEmpty(false)?.data)
    }

    @Test fun rejectsWrongFormatFutureSchemaChangedDigestAndBrokenGzip() {
        val bytes = StudyBackupCodec.encode(sample, now)
        rejected { StudyBackupCodec.decode(gzip(envelope(bytes).put("format", "another-app").toString())) }
        rejected { StudyBackupCodec.decode(gzip(envelope(bytes).put("version", 999).toString())) }
        rejected { StudyBackupCodec.decode(gzip(envelope(bytes).put("sha256", "wrong").toString())) }
        rejected { StudyBackupCodec.decode(bytes.copyOf(bytes.size / 2)) }
        rejected { StudyBackupCodec.decode(changedState(bytes) { it.put("version", 999) }) }
    }

    @Test fun rejectsOversizedCompressedAndExpandedPayloads() {
        rejected { StudyBackupCodec.decode(ByteArray(StudyBackupCodec.MAX_COMPRESSED + 1)) }
        rejected { StudyBackupCodec.decode(gzip("x".repeat(StudyBackupCodec.MAX_EXPANDED + 1))) }
        rejected { StudyBackupCodec.readBounded(ByteArray(128).inputStream(), 127) }
    }

    @Test fun rejectsDuplicateIdentifiersAndInvalidDurationsBeforeRestore() {
        val bytes = StudyBackupCodec.encode(sample, now)
        rejected { StudyBackupCodec.decode(changedState(bytes) { it.getJSONArray("subjects").put(it.getJSONArray("subjects").getJSONObject(0)) }) }
        rejected { StudyBackupCodec.decode(changedState(bytes) { it.getJSONArray("sessions").getJSONObject(0).put("seconds", -10) }) }
        rejected { StudyBackupCodec.decode(changedState(bytes) { it.getJSONObject("timer").put("targetSeconds", Long.MAX_VALUE) }) }
    }

    @Test fun legacyStudySchemaMigratesWithoutDroppingStudies() {
        val migrated = StudyBackupCodec.decode(changedState(StudyBackupCodec.encode(sample, now)) {
            it.put("version", 1); it.remove("profile"); it.remove("settings"); it.remove("usage"); it.remove("tutorialsSeen")
        }).data
        assertEquals(sample.subjects, migrated.subjects)
        assertEquals(sample.sessions, migrated.sessions)
        assertEquals(sample.questions, migrated.questions)
        assertEquals(sample.attempts, migrated.attempts)
        assertEquals(StudentProfile(), migrated.profile)
    }
}
