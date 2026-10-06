package app.ninho.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidPlayLicenseStorage(private val context: Context) : PlayLicenseStorage {
    private val cache = AtomicFile(File(context.noBackupFilesDir, "play-acquisition-v1.bin"))
    private val suspension = AtomicFile(File(context.noBackupFilesDir, "play-acquisition-suspended-v1"))
    private val alias = "ninho-play-acquisition-v1"
    override val suspended get() = synchronized(ioLock) { listOf("", ".bak", ".new").any { File(suspension.baseFile.path + it).exists() } }

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) return null
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    override fun read(): PlayLicenseProof? = synchronized(ioLock) {
        if (!cache.baseFile.exists() && !File(cache.baseFile.path + ".bak").exists()) return@synchronized null
        val binding = key(false) ?: return@synchronized null
        val encrypted = cache.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 32800)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val json = JSONObject(String(LicenseCacheCipher.open(encrypted, binding, context.packageName), Charsets.UTF_8))
        PlayLicenseProof(json.getString("signedData"), json.getString("signature"), json.getLong("acceptedAt"))
    }

    override fun write(proof: PlayLicenseProof) = synchronized(ioLock) {
        val json = JSONObject().put("signedData", proof.signedData).put("signature", proof.signature).put("acceptedAt", proof.acceptedAt)
        val bytes = LicenseCacheCipher.seal(json.toString().toByteArray(Charsets.UTF_8), checkNotNull(key(true)), context.packageName)
        writeAtomic(cache, bytes)
    }

    override fun suspendAccess() = synchronized(ioLock) {
        val marker = "suspended-v1".toByteArray(Charsets.UTF_8)
        writeAtomic(suspension, marker)
        check(suspension.baseFile.length() == marker.size.toLong() && suspension.baseFile.readBytes().contentEquals(marker))
    }

    override fun resumeAccess() = synchronized(ioLock) {
        // Keep the primary marker until every old AtomicFile candidate has been removed.
        for (suffix in listOf(".new", ".bak", "")) {
            val file = File(suspension.baseFile.path + suffix)
            check(!file.exists() || file.delete()) { "Não foi possível concluir a confirmação da compra." }
        }
        check(!suspended)
    }

    override fun discard(): Unit = synchronized(ioLock) {
        cache.delete()
        val keys = KeyStore.getInstance("AndroidKeyStore")
        keys.load(null)
        keys.deleteEntry(alias)
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val output = file.startWrite()
        try { output.write(bytes); output.fd.sync(); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    companion object { private val ioLock = Any() }
}
