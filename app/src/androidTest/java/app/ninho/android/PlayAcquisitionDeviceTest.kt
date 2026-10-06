package app.ninho.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object PlayAcquisitionDeviceTest {
    fun run(context: Context): String {
        val marker = UUID.randomUUID().toString()
        val aliases = listOf("ninho-test-license-$marker-a", "ninho-test-license-$marker-b")
        val root = File(context.cacheDir, "license-test-$marker").apply { check(mkdirs()) }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        fun generate(alias: String): SecretKey = KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
        var checks = 0
        fun expect(value: Boolean) { check(value); checks++ }
        try {
            val first = generate(aliases[0])
            val otherInstall = generate(aliases[1])
            expect(first.encoded == null && otherInstall.encoded == null)
            val plaintext = "synthetic-license-proof-not-a-purchase".toByteArray()
            val encrypted = LicenseCacheCipher.seal(plaintext, first, context.packageName)
            expect(LicenseCacheCipher.open(encrypted, first, context.packageName).contentEquals(plaintext))
            expect(runCatching { LicenseCacheCipher.open(encrypted, otherInstall, context.packageName) }.isFailure)
            expect(runCatching { LicenseCacheCipher.open(encrypted, first, "another.package") }.isFailure)
            expect(runCatching { LicenseCacheCipher.open(encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }, first, context.packageName) }.isFailure)
            val file = AtomicFile(File(root, "proof.bin"))
            val output = file.startWrite()
            try { output.write(encrypted); file.finishWrite(output) }
            catch (error: Exception) { file.failWrite(output); throw error }
            val persisted = file.openRead().use { it.readBytes() }
            expect(LicenseCacheCipher.open(persisted, first, context.packageName).contentEquals(plaintext))
            store.deleteEntry(aliases[0])
            val fresh = generate(aliases[0])
            expect(runCatching { LicenseCacheCipher.open(persisted, fresh, context.packageName) }.isFailure)
            return "PASS: $checks cenários AndroidKeyStore/AtomicFile reais, chaves não exportáveis e cache rejeitado após troca de chave/instalação. Sem consulta Play ou compra real.\n"
        } finally {
            aliases.forEach { store.deleteEntry(it) }
            check(root.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            root.deleteRecursively()
        }
    }
}
