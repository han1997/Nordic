package com.nordic.mediahub.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

object NordicMotion {
    const val durationShort = 200
    const val durationMedium = 300
    const val durationLong = 450

    val easingStandard: Easing = FastOutSlowInEasing
    val easingDecelerate: Easing = LinearOutSlowInEasing
    val easingAccelerate: Easing = FastOutLinearInEasing

    val enterSlideUp: EnterTransition = slideInVertically(
        animationSpec = tween(durationMedium, easing = easingStandard),
        initialOffsetY = { it }
    ) + fadeIn(
        animationSpec = tween(durationMedium, easing = easingStandard)
    )

    val exitSlideDown: ExitTransition = slideOutVertically(
        animationSpec = tween(durationMedium, easing = easingStandard),
        targetOffsetY = { it }
    ) + fadeOut(
        animationSpec = tween(durationMedium, easing = easingStandard)
    )

    val enterFade: EnterTransition = fadeIn(
        animationSpec = tween(durationMedium, easing = easingStandard)
    )

    val exitFade: ExitTransition = fadeOut(
        animationSpec = tween(durationMedium, easing = easingStandard)
    )

    val crossfadeSpec: ContentTransform = fadeIn(
        animationSpec = tween(durationMedium, easing = easingStandard)
    ) togetherWith fadeOut(
        animationSpec = tween(durationMedium, easing = easingStandard)
    )

    fun slideDirectionSpec(forward: Boolean): ContentTransform {
        return if (forward) {
            slideInHorizontally(
                animationSpec = tween(durationMedium, easing = easingStandard),
                initialOffsetX = { it }
            ) + fadeIn(
                animationSpec = tween(durationMedium, easing = easingStandard)
            ) togetherWith slideOutHorizontally(
                animationSpec = tween(durationMedium, easing = easingStandard),
                targetOffsetX = { -it }
            ) + fadeOut(
                animationSpec = tween(durationMedium, easing = easingStandard)
            )
        } else {
            slideInHorizontally(
                animationSpec = tween(durationMedium, easing = easingStandard),
                initialOffsetX = { -it }
            ) + fadeIn(
                animationSpec = tween(durationMedium, easing = easingStandard)
            ) togetherWith slideOutHorizontally(
                animationSpec = tween(durationMedium, easing = easingStandard),
                targetOffsetX = { it }
            ) + fadeOut(
                animationSpec = tween(durationMedium, easing = easingStandard)
            )
        }
    }
}
