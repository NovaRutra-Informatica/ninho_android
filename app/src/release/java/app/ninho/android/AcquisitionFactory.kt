package app.ninho.android

import android.content.Context

object AcquisitionFactory {
    fun create(context: Context): AcquisitionStore = PlayAcquisition(context, BuildConfig.PLAY_LICENSE_PUBLIC_KEY)
}
