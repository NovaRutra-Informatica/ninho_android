package app.ninho.android

import org.junit.Assert.*
import org.junit.Test

class TutorialCompletionTest {
    @Test fun failedSaveKeepsModalOpenAndAllowsRetryWhileDuplicateTapsAreIgnored() {
        val completion = TutorialCompletion()
        var writes = 0
        var closed = false
        var saving = false
        var failures = 0
        lateinit var saved: () -> Unit
        lateinit var failed: () -> Unit
        fun submit() = completion.submit(persist = { success, failure -> writes++; saved = success; failed = failure },
            onPending = { saving = it }, onComplete = { closed = true }, onFailure = { failures++ })
        submit()
        submit()
        assertTrue(saving)
        assertFalse(closed)
        assertEquals(1, writes)
        failed()
        assertFalse(saving)
        assertFalse(closed)
        assertEquals(1, failures)
        val stale = saved
        submit()
        stale()
        assertFalse(closed)
        assertTrue(saving)
        saved()
        assertTrue(closed)
        assertFalse(saving)
        submit()
        assertEquals(2, writes)
    }
}
