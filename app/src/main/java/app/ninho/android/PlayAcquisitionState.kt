package app.ninho.android

internal interface PlayLicenseStorage {
    val suspended: Boolean
    fun read(): PlayLicenseProof?
    fun write(proof: PlayLicenseProof)
    fun suspendAccess()
    fun resumeAccess()
    fun discard()
}

internal class PlayAcquisitionState(private val verifier: PlayLicenseVerifier, private val storage: PlayLicenseStorage, private val version: Long) {
    fun cached(): AcquisitionResult = try {
        val proof = if (storage.suspended) null else storage.read()
        AcquisitionResult(if (proof != null && verifier.cached(proof, version) && !storage.suspended) AcquisitionStatus.GRANTED else AcquisitionStatus.NEEDS_PROOF)
    } catch (_: Exception) { AcquisitionResult(AcquisitionStatus.NEEDS_PROOF) }

    fun begin(): PlayLicenseProof? {
        val previous = runCatching { if (storage.suspended) null else storage.read()?.takeIf { verifier.cached(it, version) } }.getOrNull()
        storage.suspendAccess()
        check(storage.suspended)
        return previous
    }

    fun received(code: Int, data: String, signature: String, nonce: Long, now: Long): AcquisitionResult {
        check(storage.suspended)
        val response = verifier.received(code, data, signature, nonce, version, now)
        if (response.code == PlayLicenseVerifier.NOT_LICENSED) {
            runCatching { storage.discard() }
            return AcquisitionResult(AcquisitionStatus.NOT_LICENSED)
        }
        return publish(PlayLicenseProof(data, signature, now))
    }

    fun recoverNetworkFailure(previous: PlayLicenseProof?): AcquisitionResult = try {
        if (previous != null && verifier.cached(previous, version)) publish(previous)
        else AcquisitionResult(AcquisitionStatus.UNAVAILABLE)
    } catch (_: Exception) { AcquisitionResult(AcquisitionStatus.UNAVAILABLE) }

    private fun publish(proof: PlayLicenseProof): AcquisitionResult {
        check(storage.suspended && verifier.cached(proof, version))
        storage.write(proof)
        check(storage.read() == proof)
        storage.resumeAccess()
        check(!storage.suspended)
        return AcquisitionResult(AcquisitionStatus.GRANTED)
    }
}
