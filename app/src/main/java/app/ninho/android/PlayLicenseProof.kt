package app.ninho.android

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PlayLicenseProof(val signedData: String, val signature: String, val acceptedAt: Long)
data class PlayLicenseResponse(val code: Int, val nonce: Long, val packageName: String, val version: Long, val timestamp: Long)

class PlayLicenseVerifier(encodedKey: String, private val packageName: String) {
    private val key: PublicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey))).also {
        require(it is RSAPublicKey && it.modulus.bitLength() >= 2048) { "Chave pública de licença inválida." }
    }

    fun received(code: Int, signedData: String, signature: String, nonce: Long, version: Long, now: Long): PlayLicenseResponse {
        val response = verified(signedData, signature)
        require(response.code == code && response.nonce == nonce && response.version == version)
        require(now > 0 && response.timestamp >= now - FRESHNESS && response.timestamp <= now + CLOCK_SKEW)
        return response
    }

    fun cached(proof: PlayLicenseProof, installedVersion: Long): Boolean = runCatching {
        val response = verified(proof.signedData, proof.signature)
        require(response.code == LICENSED || response.code == LICENSED_OLD_KEY)
        require(response.version in 1..installedVersion)
        require(proof.acceptedAt > 0 && response.timestamp >= proof.acceptedAt - FRESHNESS && response.timestamp <= proof.acceptedAt + CLOCK_SKEW)
        true
    }.getOrDefault(false)

    private fun verified(raw: String, signature: String): PlayLicenseResponse {
        require(raw.length in 1..16384 && signature.length in 1..4096)
        // SHA1withRSA is the Google Play LVL wire protocol, not a choice for new signatures.
        require(Signature.getInstance("SHA1withRSA").run {
            initVerify(key); update(raw.toByteArray(Charsets.UTF_8)); verify(Base64.getDecoder().decode(signature))
        })
        val fields = raw.substringBefore(':').split('|')
        require(fields.size == 6 && fields[2] == packageName && fields[4].isNotBlank() && fields[4].length <= 1024)
        val code = fields[0].toInt()
        require(code in setOf(LICENSED, NOT_LICENSED, LICENSED_OLD_KEY))
        val version = fields[3].toLong()
        val timestamp = fields[5].toLong()
        require(version > 0 && timestamp > 0)
        return PlayLicenseResponse(code, fields[1].toLong(), fields[2], version, timestamp)
    }

    companion object {
        const val LICENSED = 0
        const val NOT_LICENSED = 1
        const val LICENSED_OLD_KEY = 2
        private const val FRESHNESS = 24 * 60 * 60 * 1000L
        private const val CLOCK_SKEW = 10 * 60 * 1000L
    }
}

object LicenseCacheCipher {
    private fun aad(packageName: String) = "ninho-play-license-cache-v1|$packageName".toByteArray(Charsets.UTF_8)
    fun seal(plain: ByteArray, key: SecretKey, packageName: String): ByteArray {
        require(plain.size in 1..32768)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad(packageName))
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
    }
    fun open(bytes: ByteArray, key: SecretKey, packageName: String): ByteArray {
        require(bytes.size in 30..32800 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad(packageName))
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }
}

enum class AcquisitionStatus { CHECKING, NEEDS_PROOF, GRANTED, UNAVAILABLE, NOT_LICENSED, CONFIGURATION_ERROR }
data class AcquisitionResult(val status: AcquisitionStatus, val detail: String = "")
interface AcquisitionStore {
    fun cached(): AcquisitionResult
    suspend fun verify(): AcquisitionResult
}
