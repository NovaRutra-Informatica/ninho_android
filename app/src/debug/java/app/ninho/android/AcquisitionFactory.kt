package app.ninho.android

import android.content.Context

object AcquisitionFactory {
    fun create(context: Context): AcquisitionStore = object : AcquisitionStore {
        override fun cached() = AcquisitionResult(AcquisitionStatus.GRANTED)
        override suspend fun verify() = cached()
    }
}
