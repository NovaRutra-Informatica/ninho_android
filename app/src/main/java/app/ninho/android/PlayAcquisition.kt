package app.ninho.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.android.vending.licensing.ILicenseResultListener
import com.android.vending.licensing.ILicensingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class PlayAcquisition(private val context: Context, publicKey: String) : AcquisitionStore {
    private val verifier = runCatching { PlayLicenseVerifier(publicKey, context.packageName) }.getOrNull()
    private val storage = AndroidPlayLicenseStorage(context)
    private val version get() = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    override fun cached(): AcquisitionResult {
        val check = verifier ?: return AcquisitionResult(AcquisitionStatus.CONFIGURATION_ERROR)
        return try { PlayAcquisitionState(check, storage, version).cached() }
        catch (_: Exception) { AcquisitionResult(AcquisitionStatus.NEEDS_PROOF) }
    }

    override suspend fun verify(): AcquisitionResult = verificationLock.withLock {
        val check = verifier ?: return@withLock AcquisitionResult(AcquisitionStatus.CONFIGURATION_ERROR)
        val state = try { PlayAcquisitionState(check, storage, version) }
        catch (_: Exception) { return@withLock AcquisitionResult(AcquisitionStatus.UNAVAILABLE) }
        val previous = try { withContext(Dispatchers.IO) { state.begin() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock AcquisitionResult(AcquisitionStatus.UNAVAILABLE) }
        val nonce = SecureRandom().nextInt().toLong()
        val response = try { withTimeout(20_000) { request(nonce) } }
        catch (_: kotlinx.coroutines.TimeoutCancellationException) { return@withLock withContext(Dispatchers.IO) { state.recoverNetworkFailure(previous) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock AcquisitionResult(AcquisitionStatus.UNAVAILABLE) }
        withContext(Dispatchers.IO) {
            if (response.code in setOf(-1, 4, 5, 0x101)) state.recoverNetworkFailure(previous)
            else try { state.received(response.code, response.data, response.signature, nonce, System.currentTimeMillis()) }
            catch (_: Exception) { AcquisitionResult(AcquisitionStatus.UNAVAILABLE) }
        }
    }

    companion object { private val verificationLock = Mutex() }

    private data class WireResponse(val code: Int, val data: String, val signature: String)
    private suspend fun request(nonce: Long): WireResponse = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean(false)
            var bound = false
            lateinit var connection: ServiceConnection
            fun unbind() { if (bound) { bound = false; runCatching { context.unbindService(connection) } } }
            fun finish(response: WireResponse) {
                if (completed.compareAndSet(false, true)) {
                    unbind()
                    if (continuation.isActive) continuation.resume(response)
                }
            }
            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    if (!continuation.isActive || completed.get()) { unbind(); return }
                    try {
                        ILicensingService.Stub.asInterface(service).checkLicense(nonce, context.packageName, object : ILicenseResultListener.Stub() {
                            override fun verifyLicense(code: Int, data: String?, signature: String?) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post { finish(WireResponse(code, data.orEmpty(), signature.orEmpty())) }
                            }
                        })
                    } catch (_: Exception) { finish(WireResponse(-1, "", "")) }
                }
                override fun onServiceDisconnected(name: ComponentName) { finish(WireResponse(-1, "", "")) }
                override fun onNullBinding(name: ComponentName) { finish(WireResponse(-1, "", "")) }
                override fun onBindingDied(name: ComponentName) { finish(WireResponse(-1, "", "")) }
            }
            continuation.invokeOnCancellation {
                completed.set(true)
                android.os.Handler(android.os.Looper.getMainLooper()).post { unbind() }
            }
            try {
                if (!continuation.isActive) return@suspendCancellableCoroutine
                bound = context.bindService(Intent("com.android.vending.licensing.ILicensingService").setPackage("com.android.vending"), connection, Context.BIND_AUTO_CREATE)
                if (!bound) finish(WireResponse(-1, "", ""))
            } catch (_: Exception) { finish(WireResponse(-1, "", "")) }
        }
    }
}
