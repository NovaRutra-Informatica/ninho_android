package app.ninho.android

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

internal fun reducedMotion(preference: Boolean) = preference || !ValueAnimator.areAnimatorsEnabled()

@Composable internal fun NinhoMascot(size: Dp, reduced: Boolean, modifier: Modifier = Modifier, trigger: Any = Unit) {
    val motion = reducedMotion(reduced)
    val pose = remember { Animatable(0f) }
    LaunchedEffect(motion, trigger) {
        pose.snapTo(0f)
        if (!motion) pose.animateTo(0f, keyframes { durationMillis = 700; 1f at 220; -.45f at 450; 0f at 700 })
    }
    Image(painterResource(R.drawable.owl), null, modifier.size(size).graphicsLayer {
        val amount = if (motion) 0f else pose.value
        scaleX = 1 + kotlin.math.abs(amount) * .035f
        scaleY = scaleX
        rotationZ = -3 * amount
        translationY = -2 * kotlin.math.abs(amount) * density
    })
}
