package app.ninho.android

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors

class NinhoModel(application: Application) : AndroidViewModel(application) {
    val bootCount = android.provider.Settings.Global.getInt(application.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    private val store = StudyStore(application)
    private val coach = LocalCoach(application)
    private val reminder = TimerReminder(application)
    private val welcomePreferences = application.getSharedPreferences("welcome-flow", android.content.Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inference = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var aiJob: Job? = null
    private val writes = Mutex()
    private var pendingWrites = 0
    private var foreground = false
    private var activeRoute = ""
    private val usageClock = ActiveRouteClock()
    private var usageSeconds = 0L
    private var usagePulse: Job? = null
    private var analysisJob: Job? = null
    private var backupRefreshJob: Job? = null
    private var analysisVersion = 0
    private var lastAnalysisMono = -5000L
    var analysis by mutableStateOf(LocalAnalysis()); private set
    var analysisBusy by mutableStateOf(false); private set
    var focusRequest by mutableIntStateOf(0); private set
    var data by mutableStateOf(StudyData()); private set
    var profileDraft by mutableStateOf(StudentProfile()); private set
    var profileUnsaved by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var aiBusy by mutableStateOf(false); private set
    var aiStatus by mutableStateOf("Modelo de linguagem opcional. A análise incluída funciona sem importação."); private set
    var aiResponse by mutableStateOf(""); private set
    var modelName by mutableStateOf(coach.modelName); private set
    var modelBytes by mutableLongStateOf(coach.modelBytes); private set
    var routeRequest by mutableStateOf("today" to 0); private set
    var onboardingProcessing by mutableStateOf(false); private set
    var welcomeStep by mutableIntStateOf(0); private set
    var backupBusy by mutableStateOf(false); private set
    var backupNotice by mutableStateOf<String?>(null); private set
    var lastBackupAt by mutableStateOf<Long?>(null); private set
    var pendingBackup by mutableStateOf<RestorableStudyBackup?>(null); private set
    init {
        scope.launch {
            try {
                data = withContext(Dispatchers.IO) {
                    val saved = store.load()
                    val restored = if (saved.profile.planStatus == "pending") saved.copy(profile = saved.profile.copy(planStatus = "error")) else saved
                    restored.copy(usage = UsageLedger.prune(restored.usage, System.currentTimeMillis()))
                }
                val savedStep = withContext(Dispatchers.IO) { welcomePreferences.getInt("question-step-v2", 0) }
                welcomeStep = WelcomeFlow.resumeAt(savedStep, data.profile)
                loaded = true
                profileDraft = data.profile
                updateBackupStatus()
                try { withContext(Dispatchers.IO) { StudyBackupJob.schedule(application) } }
                catch (_: Exception) { backupNotice = "A cópia diária continua ao abrir ou salvar estudos. O Android não permitiu agendar a execução em repouso." }
                if (foreground) { beginUsageVisit(); requestAnalysis(force = true) }
                syncReminder()
                syncWidgets()
                if (data.profile.planStatus == "error") aiStatus = "A última análise não foi concluída. Seu perfil está salvo; você pode gerar o plano novamente."
            }
            catch (_: Exception) { error = "Não foi possível ler seus estudos. Os arquivos foram preservados. Reinicie o app para tentar novamente." }
        }
        if (modelName != null) aiStatus = "Modelo importado. A compatibilidade será verificada ao gerar a orientação."
    }
    fun dismissError() { error = null }
    private fun change(afterSaved: (() -> Unit)? = null, onFailure: (() -> Unit)? = null, transform: (StudyData) -> StudyData) {
        if (!loaded) return
        pendingWrites++; saving = true
        scope.launch {
            try {
                writes.withLock {
                    val previous = data
                    val next = transform(previous)
                    withContext(Dispatchers.IO) { store.save(next) }
                    data = next
                    updateBackupStatus()
                    queueBackupRefresh()
                    if (next.sessions != previous.sessions || next.attempts != previous.attempts || next.subjects != previous.subjects || next.profile.dailyMinutes != previous.profile.dailyMinutes || next.profile.completedAt != previous.profile.completedAt || next.timer != previous.timer) syncWidgets()
                    if (next.timer != previous.timer || next.settings.timerReminder != previous.settings.timerReminder)
                        syncReminder()
                    if (next.profile.revision != previous.profile.revision || next.subjects != previous.subjects || next.sessions != previous.sessions || next.attempts != previous.attempts || next.usage != previous.usage) requestAnalysis()
                }
                afterSaved?.invoke()
            }
            catch (_: Exception) { error = "Não foi possível salvar. Libere espaço e tente novamente; o registro anterior foi preservado."; onFailure?.invoke() }
            finally { pendingWrites--; saving = pendingWrites > 0 }
        }
    }
    fun updateProfile(draft: StudentProfile) {
        profileDraft = draft.normalized()
        val snapshot = profileDraft
        profileUnsaved = true
        change(afterSaved = { if (profileDraft == snapshot) profileUnsaved = false }) { it.copy(profile = it.profile.edited(snapshot, System.currentTimeMillis())) }
    }
    private fun updateBackupStatus() { backupNotice = store.backupNotice; lastBackupAt = store.lastBackupAt }
    private fun queueBackupRefresh() {
        backupRefreshJob?.cancel()
        backupRefreshJob = scope.launch {
            delay(2000)
            try { withContext(Dispatchers.IO) { store.refreshBackupFromDisk() }; updateBackupStatus() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { backupNotice = "Seus estudos estão salvos, mas o backup leve não pôde ser atualizado agora." }
        }
    }
    fun exportBackup(uri: Uri) {
        if (!loaded || backupBusy) return
        backupBusy = true
        scope.launch {
            try {
                writes.withLock {
                    val current = data
                    withContext(Dispatchers.IO) {
                        val bytes = store.exportBackup(current)
                        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
                            ?: error("Não foi possível abrir o destino.")
                    }
                }
                backupNotice = "Backup exportado para o local escolhido. Arquivos e modelos ficam de fora."
            } catch (_: Exception) { backupNotice = "Não foi possível exportar. Seus estudos estão salvos; escolha outro destino ou verifique o espaço." }
            finally { backupBusy = false }
        }
    }
    fun inspectBackup(uri: Uri) {
        if (!loaded || backupBusy) return
        backupBusy = true
        scope.launch {
            try {
                pendingBackup = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { StudyBackupCodec.readBounded(it) }
                        ?: error("Não foi possível ler o arquivo.")
                    StudyBackupCodec.decode(bytes)
                }
            } catch (_: Exception) { backupNotice = "Não foi possível validar este backup. Escolha um arquivo .ninho-backup.gz de até 2 MB, criado pelo Ninho Android. Seus estudos atuais foram mantidos." }
            finally { backupBusy = false }
        }
    }
    fun cancelBackupRestore() { if (!backupBusy) pendingBackup = null }
    fun restoreInspectedBackup() {
        val proposed = pendingBackup ?: return
        if (backupBusy || saving) return
        backupBusy = true
        cancelAi()
        scope.launch {
            try {
                writes.withLock {
                    withContext(Dispatchers.IO) { store.restoreBackup(proposed.data) }
                    data = proposed.data
                    profileDraft = data.profile
                    profileUnsaved = false
                    welcomeStep = WelcomeFlow.resumeAt(0, data.profile)
                    pendingBackup = null
                    updateBackupStatus()
                    usageSeconds = 0
                    usageClock.begin(SystemClock.elapsedRealtime())
                    syncReminder(); syncWidgets(); requestAnalysis(force = true)
                }
            } catch (_: Exception) { backupNotice = "Não foi possível restaurar. Os dados anteriores foram preservados; confira o espaço disponível." }
            finally { backupBusy = false }
        }
    }
    fun updateTheme(theme: String) = change { it.copy(settings = it.settings.copy(theme = theme.takeIf { t -> t in listOf("system", "light", "dark") } ?: "system")) }
    fun updateReducedMotion(reduced: Boolean) = change { it.copy(settings = it.settings.copy(reducedMotion = reduced)) }
    fun tutorialSeen(route: String) = change { it.copy(tutorialsSeen = it.tutorialsSeen + route) }
    fun resetTutorials() = change { it.copy(tutorialsSeen = emptySet()) }
    fun updateTimerReminder(enabled: Boolean) = change { it.copy(settings = it.settings.copy(timerReminder = enabled)) }
    fun canNotify() = reminder.canNotify()
    private suspend fun syncReminder() {
        try { withContext(Dispatchers.IO) { reminder.sync() } }
        catch (_: Exception) { error = "Seus dados foram salvos, mas o aviso do temporizador não pôde ser agendado pelo Android. O tempo continua preservado." }
    }
    fun updateUsage(enabled: Boolean) {
        flushUsage()
        change(afterSaved = { if (enabled && foreground) beginUsageVisit() }) { it.copy(settings = it.settings.copy(appUsage = enabled)) }
    }
    fun clearUsage() { usageSeconds = 0; usageClock.begin(SystemClock.elapsedRealtime()); change { it.copy(usage = emptyList()) } }
    fun openFocusFromNotification() { focusRequest++ }
    fun openRoute(route: String) { if (route in setOf("today", "focus", "reviews")) routeRequest = route to (routeRequest.second + 1) }
    private suspend fun syncWidgets() {
        try { withContext(Dispatchers.IO) { NinhoWidgets.refresh(getApplication(), rebuild = true) } }
        catch (_: Exception) { error = "Seus estudos estão salvos, mas os widgets não puderam ser atualizados. Abra o Ninho novamente para tentar." }
    }
    fun setForeground(active: Boolean) {
        if (foreground == active) return
        if (!active) {
            flushUsage(); foreground = false; usagePulse?.cancel(); analysisVersion++; analysisJob?.cancel(); analysisBusy = false
            if (aiBusy) cancelAi()
            return
        }
        foreground = true; beginUsageVisit(); requestAnalysis()
        usagePulse = scope.launch { while (isActive && foreground) { delay(30_000); flushUsage() } }
        if (loaded) scope.launch { syncReminder(); syncWidgets() }
    }
    fun visitRoute(route: String) {
        if (activeRoute == route) return
        flushUsage(); activeRoute = route
        if (foreground) beginUsageVisit()
    }
    private fun trackUsage() = loaded && foreground && data.settings.appUsage && data.profile.completedAt != null && activeRoute in UsageLedger.routes
    private fun beginUsageVisit() {
        usageClock.begin(SystemClock.elapsedRealtime()); usageSeconds = 0
        if (trackUsage()) { val route = activeRoute; val now = System.currentTimeMillis(); change { it.copy(usage = UsageLedger.add(it.usage, route, now, visits = 1)) } }
    }
    fun recordInteraction() {
        if (!trackUsage()) return
        val now = SystemClock.elapsedRealtime(); usageSeconds += usageClock.takeSeconds(now); usageClock.touch(now)
    }
    private fun flushUsage() {
        if (!trackUsage()) { usageSeconds = 0; return }
        val seconds = usageSeconds + usageClock.takeSeconds(SystemClock.elapsedRealtime()); usageSeconds = 0
        if (seconds <= 0) return
        val route = activeRoute; val now = System.currentTimeMillis()
        change { it.copy(usage = UsageLedger.add(it.usage, route, now, seconds = seconds)) }
    }
    private fun requestAnalysis(force: Boolean = false) {
        if (!loaded || !foreground) return
        analysisJob?.cancel(); val version = ++analysisVersion
        analysisJob = scope.launch {
            try {
                if (!force) delay(maxOf(750, 5000 - (SystemClock.elapsedRealtime() - lastAnalysisMono)))
                if (!foreground) return@launch
                analysisBusy = true
                val snapshot = data
                val result = withContext(Dispatchers.Default) { LocalAnalytics.analyze(snapshot, System.currentTimeMillis()) }
                if (foreground && version == analysisVersion) { analysis = result; lastAnalysisMono = SystemClock.elapsedRealtime() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (version == analysisVersion) error = "Não foi possível preparar as sugestões agora. Seus registros permanecem salvos." }
            finally { if (version == analysisVersion) analysisBusy = false }
        }
    }
    fun retryAnalysis() = requestAnalysis(force = true)
    fun prepareInsight(insight: LocalInsight) {
        if (insight.route == "focus" && !data.timer.running && data.timer.elapsedMillis == 0L) {
            val subject = insight.subjectId.ifBlank { data.timer.subjectId.ifBlank { data.subjects.firstOrNull()?.id.orEmpty() } }
            configureTimer(subject, minOf(data.profile.sessionMinutes, data.profile.dailyMinutes).coerceIn(1, 240))
        }
    }
    @android.annotation.SuppressLint("ApplySharedPref", "UseKtx") // IO; check commit's Boolean before changing steps. KTX edit returns Unit.
    fun moveWelcomeStep(next: Int) {
        if (!loaded || saving || aiBusy || next !in WelcomeFlow.steps.indices || kotlin.math.abs(next - welcomeStep) != 1) return
        if (next > welcomeStep && (profileUnsaved || !WelcomeFlow.canContinue(welcomeStep, data.profile))) return
        pendingWrites++; saving = true
        scope.launch {
            try {
                writes.withLock {
                    withContext(Dispatchers.IO) { check(welcomePreferences.edit().putInt("question-step-v2", next).commit()) }
                }
                welcomeStep = next
            }
            catch (_: Exception) { error = "Não foi possível guardar esta etapa. Suas respostas foram preservadas; tente continuar novamente." }
            finally { pendingWrites--; saving = pendingWrites > 0 }
        }
    }
    fun completeOnboarding() {
        if (saving || profileUnsaved || !WelcomeFlow.canFinish(welcomeStep, data.profile)) return
        onboardingProcessing = true
        change(afterSaved = { requestAnalysis(force = true) }, onFailure = { onboardingProcessing = false }) {
            it.copy(profile = it.profile.copy(completedAt = System.currentTimeMillis()))
        }
    }
    fun finishWelcome() { if (data.profile.completedAt != null && analysis.cards.isNotEmpty()) onboardingProcessing = false }
    fun addSubject(name: String) {
        val normalized = name.trim().take(80)
        if (normalized.isEmpty()) return
        if (data.subjects.any { it.name.equals(normalized, true) }) { error = "Essa matéria já está no seu ninho."; return }
        change { it.copy(subjects = it.subjects + Subject(name = normalized)) }
    }
    fun configureTimer(subject: String = data.timer.subjectId, minutes: Int = (data.timer.targetSeconds / 60).toInt(), stopwatch: Boolean = data.timer.stopwatch) {
        if (data.timer.running || data.timer.elapsedMillis > 0) return
        if (minutes !in 1..240) return
        change { it.copy(timer = FocusTimer(subjectId = subject, targetSeconds = minutes * 60L, stopwatch = stopwatch)) }
    }
    fun toggleTimer() {
        if (data.timer.subjectId.isBlank()) { error = "Escolha uma matéria antes de começar."; return }
        val mono = SystemClock.elapsedRealtime(); val wall = System.currentTimeMillis()
        change {
            val pausedEarly = it.timer.running && (it.timer.stopwatch || it.timer.elapsed(mono, wall, bootCount) < it.timer.targetSeconds * 1000)
            it.copy(timer = if (it.timer.running) it.timer.paused(mono, wall, bootCount) else it.timer.resumed(mono, wall, bootCount),
                usage = if (pausedEarly && it.settings.appUsage) UsageLedger.add(it.usage, "focus", wall, pauses = 1) else it.usage)
        }
    }
    fun pauseTimer() { if (data.timer.running) toggleTimer() }
    fun resetTimer() = change { it.copy(timer = FocusTimer(subjectId = it.timer.subjectId, targetSeconds = it.timer.targetSeconds, stopwatch = it.timer.stopwatch)) }
    fun saveSession(rating: Int, note: String) {
        val timer = data.timer
        val seconds = timer.elapsed(SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootCount) / 1000
        if (seconds < 1 || timer.subjectId.isBlank()) return
        change { it.copy(sessions = it.sessions + StudySession(subjectId = timer.subjectId, seconds = seconds, at = System.currentTimeMillis(), rating = rating.coerceIn(1, 3), note = note.trim().take(1000)), timer = FocusTimer(subjectId = timer.subjectId, targetSeconds = timer.targetSeconds, stopwatch = timer.stopwatch)) }
    }
    fun addQuestion(subject: String, prompt: String, answer: String) {
        if (subject.isBlank() || prompt.isBlank() || answer.isBlank()) return
        change { it.copy(questions = it.questions + Question(subjectId = subject, prompt = prompt.trim().take(2000), answer = answer.trim().take(3000))) }
    }
    fun answer(question: Question, correct: Boolean, response: String) = change { it.copy(attempts = it.attempts + Attempt(question.id, question.subjectId, correct, System.currentTimeMillis(), response.trim().take(3000))) }
    fun importModel(uri: Uri) {
        if (aiBusy) return
        aiBusy = true; aiStatus = "Copiando modelo para este aparelho…"
        aiJob = scope.launch {
            try { withContext(Dispatchers.IO) { coach.importModel(uri) }; modelName = coach.modelName; modelBytes = coach.modelBytes; aiStatus = "Modelo importado. Gere uma orientação para verificar a compatibilidade."; aiResponse = "" }
            catch (_: CancellationException) { aiStatus = "Importação cancelada." }
            catch (e: Exception) { aiStatus = e.message ?: "Não foi possível importar o modelo." }
            finally { aiBusy = false }
        }
    }
    fun generate(plan: Boolean = false) {
        if (aiBusy || modelName == null || !foreground) return
        if (profileUnsaved) { aiStatus = "Salve as alterações pendentes do perfil antes de iniciar outra análise."; return }
        val snapshot = data
        aiBusy = true; aiResponse = ""; aiStatus = "Preparando o modelo e analisando seus registros no aparelho…"; coach.prepare()
        if (plan) change { it.copy(profile = it.profile.copy(planStatus = "pending")) }
        aiJob = scope.launch {
            try {
                val response = withContext(inference) { coach.generate(snapshot, plan) }.trim()
                check(response.isNotBlank()) { "O modelo não retornou uma orientação." }
                aiResponse = response
                aiStatus = "Orientação gerada no aparelho. Confira as sugestões antes de usá-las."
                if (plan) change { it.copy(profile = it.profile.withPlan(response, snapshot.profile.revision)) }
            }
            catch (_: CancellationException) { aiStatus = "Análise cancelada. Seu perfil continua salvo."; if (plan) markPlanError(snapshot.profile.revision) }
            catch (e: Exception) { aiStatus = "Não foi possível executar este modelo. Verifique se é um .litertlm de texto compatível e se há memória livre. ${e.message.orEmpty().take(160)}"; if (plan) markPlanError(snapshot.profile.revision) }
            catch (_: LinkageError) { aiStatus = "Este processador não é compatível com o motor de IA incluído."; if (plan) markPlanError(snapshot.profile.revision) }
            finally { aiBusy = false }
        }
    }
    private fun markPlanError(revision: Int) = change { if (it.profile.revision == revision) it.copy(profile = it.profile.copy(planStatus = "error")) else it }
    fun cancelAi() { aiStatus = "Cancelando…"; coach.cancel(); aiJob?.cancel() }
    fun removeModel() {
        if (aiBusy) return
        aiBusy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { coach.removeModel() }; modelName = coach.modelName; modelBytes = coach.modelBytes; aiResponse = ""; aiStatus = "Modelo removido. Seus estudos continuam salvos." }
            catch (_: Exception) { aiStatus = "Não foi possível remover o modelo. Tente novamente." }
            finally { aiBusy = false }
        }
    }
    override fun onCleared() { coach.cancel(); scope.cancel(); inference.close() }
}
