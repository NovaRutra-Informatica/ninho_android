package app.ninho.android

internal class TutorialCompletion {
    private var pending = false
    private var completed = false
    private var request = 0

    fun submit(persist: (() -> Unit, () -> Unit) -> Unit, onPending: (Boolean) -> Unit,
               onComplete: () -> Unit, onFailure: () -> Unit) {
        if (pending || completed) return
        val current = ++request
        pending = true
        onPending(true)
        persist({
            if (pending && current == request) {
                pending = false
                completed = true
                onPending(false)
                onComplete()
            }
        }, {
            if (pending && current == request) {
                pending = false
                onPending(false)
                onFailure()
            }
        })
    }
}
