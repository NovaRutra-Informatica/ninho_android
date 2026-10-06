package app.ninho.android

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import javax.crypto.KeyGenerator

class PlayLicenseProofTest {
    private val verifier get() = PlayLicenseVerifier(Base64.getEncoder().encodeToString(pair.public.encoded), PACKAGE)
    private fun data(code: Int = 0, nonce: Long = 4711, version: Long = 1, at: Long = NOW, name: String = PACKAGE, user: String = "test-user") = "$code|$nonce|$name|$version|$user|$at:VT=${NOW + 1}"
    private fun signed(raw: String) = Base64.getEncoder().encodeToString(Signature.getInstance("SHA1withRSA").run { initSign(pair.private); update(raw.toByteArray(Charsets.UTF_8)); sign() })
    private fun rejects(action: () -> Any?) { assertTrue(runCatching(action).isFailure) }

    @Test fun acceptsRealProtocolSignatureAndKeepsPerpetualProofAfterUpgrade() {
        val raw = data()
        assertEquals(0, verifier.received(0, raw, signed(raw), 4711, 1, NOW).code)
        val proof = PlayLicenseProof(raw, signed(raw), NOW)
        assertTrue(verifier.cached(proof, 1))
        assertTrue(verifier.cached(proof, 999))
        assertFalse(verifier.cached(proof, 0))
    }
    @Test fun refusesMissingMalformedAndUndersizedPublicKey() {
        rejects { PlayLicenseVerifier("", PACKAGE) }
        rejects { PlayLicenseVerifier("not-a-public-key", PACKAGE) }
        val weak = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        rejects { PlayLicenseVerifier(Base64.getEncoder().encodeToString(weak.public.encoded), PACKAGE) }
    }
    @Test fun refusesModifiedPayloadWrongKeyAndFakeSignature() {
        val raw = data()
        rejects { verifier.received(0, raw.replace("test-user", "other-user"), signed(raw), 4711, 1, NOW) }
        val wrong = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        rejects { PlayLicenseVerifier(Base64.getEncoder().encodeToString(wrong.public.encoded), PACKAGE).received(0, raw, signed(raw), 4711, 1, NOW) }
        rejects { verifier.received(0, raw, "invalid-base64", 4711, 1, NOW) }
    }
    @Test fun refusesCapturedResponseForAnotherRequestPackageVersionAndCode() {
        for (raw in listOf(data(nonce = 4710), data(name = "app.other"), data(version = 2), data(code = 1), data(user = ""))) {
            rejects { verifier.received(0, raw, signed(raw), 4711, 1, NOW) }
        }
    }
    @Test fun refusesOldOrFutureResponseOnAcquisitionButNotExpiryOfAcceptedProof() {
        for (raw in listOf(data(at = NOW - 86_400_001), data(at = NOW + 600_001))) {
            rejects { verifier.received(0, raw, signed(raw), 4711, 1, NOW) }
        }
        val raw = data(at = NOW - 86_400_000)
        assertTrue(verifier.cached(PlayLicenseProof(raw, signed(raw), NOW), 20))
        assertFalse(verifier.cached(PlayLicenseProof(raw, signed(raw), NOW + 1), 20))
    }
    @Test fun deniedOrRetryResponseCannotBecomeOfflineLicense() {
        val raw = data(code = 1)
        assertEquals(1, verifier.received(1, raw, signed(raw), 4711, 1, NOW).code)
        assertFalse(verifier.cached(PlayLicenseProof(raw, signed(raw), NOW), 1))
        val retry = data(code = 291)
        rejects { verifier.received(291, retry, signed(retry), 4711, 1, NOW) }
    }
    @Test fun acceptsDocumentedLicensedOldKeyResponseOnlyWithValidSignature() {
        val raw = data(code = 2)
        assertEquals(2, verifier.received(2, raw, signed(raw), 4711, 1, NOW).code)
        assertTrue(verifier.cached(PlayLicenseProof(raw, signed(raw), NOW), 1))
    }
    @Test fun rejectsOversizeMalformedAndIncompleteProofs() {
        for (raw in listOf("0|4711|$PACKAGE|1|test-user", data().substringBefore(':') + "|extra", "a".repeat(16385))) {
            rejects { verifier.received(0, raw, signed(raw), 4711, 1, NOW) }
        }
        assertFalse(verifier.cached(PlayLicenseProof(data(), signed(data()), 0), 1))
    }
    @Test fun cacheAuthenticationBindsInstallationAndPackageAndRejectsTampering() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val otherInstall = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plain = data().toByteArray()
        val sealed = LicenseCacheCipher.seal(plain, key, PACKAGE)
        assertArrayEquals(plain, LicenseCacheCipher.open(sealed, key, PACKAGE))
        rejects { LicenseCacheCipher.open(sealed, otherInstall, PACKAGE) }
        rejects { LicenseCacheCipher.open(sealed, key, "other.package") }
        val changed = sealed.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        rejects { LicenseCacheCipher.open(changed, key, PACKAGE) }
        rejects { LicenseCacheCipher.open(sealed.copyOf(20), key, PACKAGE) }
        rejects { LicenseCacheCipher.open(sealed.copyOf().apply { this[0] = 2 }, key, PACKAGE) }
        assertFalse(sealed.contentEquals(LicenseCacheCipher.seal(plain, key, PACKAGE)))
    }
    companion object {
        const val PACKAGE = "app.ninho.android"
        const val NOW = 1_790_164_800_000L
        private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }
}
