package app.ninho.android

data class SessionTotals(val count: Int = 0, val seconds: Long = 0)
data class AttemptTotals(val count: Int = 0, val correct: Int = 0)

class StudyIndex private constructor(
    private val source: StudyData,
    val subjects: Map<String, Subject>,
    val questions: Map<String, Question>,
    val sessionsBySubject: Map<String, SessionTotals>,
    val attemptsBySubject: Map<String, AttemptTotals>,
    val attemptsByQuestion: Map<String, AttemptTotals>,
    val latestSession: Map<String, StudySession>,
    val latestAttempt: Map<String, Attempt>,
) {
    fun matches(data: StudyData) = source.subjects === data.subjects && source.questions === data.questions &&
        source.sessions === data.sessions && source.attempts === data.attempts

    companion object {
        fun build(data: StudyData): StudyIndex {
            val sessions = mutableMapOf<String, SessionTotals>()
            val attempts = mutableMapOf<String, AttemptTotals>()
            val questions = mutableMapOf<String, AttemptTotals>()
            val latestSessions = mutableMapOf<String, StudySession>()
            val latestAttempts = mutableMapOf<String, Attempt>()
            for (session in data.sessions) {
                val previous = sessions[session.subjectId] ?: SessionTotals()
                sessions[session.subjectId] = SessionTotals(previous.count + 1, previous.seconds + session.seconds)
                if (latestSessions[session.subjectId]?.let { session.at > it.at } != false) latestSessions[session.subjectId] = session
            }
            for (attempt in data.attempts) {
                fun addTo(map: MutableMap<String, AttemptTotals>, key: String) {
                    val previous = map[key] ?: AttemptTotals()
                    map[key] = AttemptTotals(previous.count + 1, previous.correct + if (attempt.correct) 1 else 0)
                }
                addTo(attempts, attempt.subjectId)
                addTo(questions, attempt.questionId)
                if (latestAttempts[attempt.subjectId]?.let { attempt.at > it.at } != false) latestAttempts[attempt.subjectId] = attempt
            }
            return StudyIndex(data, data.subjects.associateBy { it.id }, data.questions.associateBy { it.id },
                sessions, attempts, questions, latestSessions, latestAttempts)
        }
    }
}
