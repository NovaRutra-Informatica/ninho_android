package app.ninho.android

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PlayAcquisitionStateTest {
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val verifier get() = PlayLicenseVerifier(Base64.getEncoder().encodeToString(key.public.encoded), PACKAGE)
    private fun proof(code: Int = 0, nonce: Long = NONCE): PlayLicenseProof {
        val raw = "$code|$nonce|$PACKAGE|1|test-user|$NOW"
        val signature = Signature.getInstance("SHA1withRSA").run { initSign(key.private); update(raw.toByteArray()); sign() }
        return PlayLicenseProof(raw, Base64.getEncoder().encodeToString(signature), NOW)
    }
    private fun state(disk: Disk) = PlayAcquisitionState(verifier, disk, 1)
    private fun respond(state: PlayAcquisitionState, code: Int, proof: PlayLicenseProof) = state.received(code, proof.signedData, proof.signature, NONCE, NOW)

    @Test fun deniedLicenseStaysBlockedAfterFileAndKeystoreDeletionFailAndProcessRestarts() {
        val old = proof()
        val disk = Disk(old).apply { failDiscard = true }
        val transaction = state(disk)
        assertEquals(old, transaction.begin())
        assertEquals(AcquisitionStatus.NOT_LICENSED, respond(transaction, 1, proof(1)).status)
        assertEquals(old, disk.proof)
        assertTrue(disk.suspended)
        assertEquals(AcquisitionStatus.NEEDS_PROOF, state(disk).cached().status)
        val retry = state(disk)
        assertNull(retry.begin())
        assertEquals(AcquisitionStatus.UNAVAILABLE, retry.recoverNetworkFailure(null).status)
        assertEquals(AcquisitionStatus.NEEDS_PROOF, state(disk).cached().status)
    }

    @Test fun failedSuspensionStopsBeforeARequestCanBeStarted() {
        val disk = Disk(proof()).apply { failSuspend = true }
        var requests = 0
        assertTrue(runCatching { state(disk).begin(); requests++ }.isFailure)
        assertEquals(0, requests)
        assertEquals(AcquisitionStatus.GRANTED, state(disk).cached().status)
    }

    @Test fun malformedOrWrongNonceReplyNeverRestoresOldProofOnRestart() {
        for (reply in listOf(proof().copy(signature = "fake"), proof(nonce = NONCE - 1))) {
            val disk = Disk(proof())
            val transaction = state(disk)
            transaction.begin()
            assertTrue(runCatching { respond(transaction, 0, reply) }.isFailure)
            assertEquals(AcquisitionStatus.NEEDS_PROOF, state(disk).cached().status)
        }
    }

    @Test fun successfulAcquisitionPublishesAndReadsBackBeforeRemovingSuspension() {
        val disk = Disk(null)
        val transaction = state(disk)
        assertNull(transaction.begin())
        assertEquals(AcquisitionStatus.GRANTED, respond(transaction, 0, proof()).status)
        assertFalse(disk.suspended)
        assertEquals(AcquisitionStatus.GRANTED, state(disk).cached().status)
    }

    @Test fun recoverableNetworkErrorCanRestoreOnlyPreviouslyValidatedProof() {
        val disk = Disk(proof())
        val transaction = state(disk)
        val previous = transaction.begin()
        assertEquals(AcquisitionStatus.GRANTED, transaction.recoverNetworkFailure(previous).status)
        assertEquals(AcquisitionStatus.GRANTED, state(disk).cached().status)
        disk.proof = proof().copy(signature = "invalid")
        assertNull(transaction.begin())
        assertEquals(AcquisitionStatus.UNAVAILABLE, transaction.recoverNetworkFailure(null).status)
        assertEquals(AcquisitionStatus.NEEDS_PROOF, state(disk).cached().status)
    }

    @Test fun failedRollbackOrPublicationNeverRevivesAnOldCacheAfterRestart() {
        for (fault in listOf("write", "resume", "readback")) {
            val disk = Disk(proof())
            val transaction = state(disk)
            val previous = transaction.begin()
            disk.fault = fault
            assertEquals(AcquisitionStatus.UNAVAILABLE, transaction.recoverNetworkFailure(previous).status)
            disk.fault = ""
            assertEquals(AcquisitionStatus.NEEDS_PROOF, state(disk).cached().status)
        }
    }

    @Test fun interruptedRequestStaysSuspendedAcrossFreshInstances() {
        val disk = Disk(proof())
        state(disk).begin()
        assertEquals(AcquisitionStatus.NEEDS_PROOF, state(disk).cached().status)
        assertNull(state(disk).begin())
    }

    private class Disk(var proof: PlayLicenseProof?) : PlayLicenseStorage {
        override var suspended = false
        var failDiscard = false
        var failSuspend = false
        var fault = ""
        override fun read() = if (fault == "readback") null else proof
        override fun write(proof: PlayLicenseProof) { if (fault == "write") throw IOException("write failed"); this.proof = proof }
        override fun suspendAccess() { if (failSuspend) throw IOException("suspension failed"); suspended = true }
        override fun resumeAccess() { if (fault == "resume") throw IOException("rollback failed"); suspended = false }
        override fun discard() { if (failDiscard) throw IOException("cache and Keystore deletion failed"); proof = null }
    }

    companion object {
        private const val PACKAGE = "app.ninho.android"
        private const val NOW = 1_790_164_800_000L
        private const val NONCE = 71L
    }
}
