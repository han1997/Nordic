package com.nordic.mediahub.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nordic.mediahub.data.stripAuthQuery

@Composable
internal fun AuthedAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    crossfadeEnabled: Boolean = true,
    onError: (() -> Unit)? = null
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    // A neutral, low-alpha placeholder so the surface never "flips" from empty to
    // image when the bitmap resolves — combined with the crossfade below this
    // removes the cover-art pop-in. `ColorDrawable` is allocation-free to reuse.
    val placeholder = remember { ColorDrawable(Color.argb(0x28, 0x80, 0x80, 0x80)) }
    val imageRequest = remember(url, crossfadeEnabled) {
        val cleanCacheKey = stripAuthQuery(url)
        ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(cleanCacheKey)
            .memoryCacheKey(cleanCacheKey)
            .apply {
                // Fast list scrolling stacks many simultaneous crossfade
                // animations on the RenderThread; list rows disable it.
                // crossfade(false) must be explicit: omitting the call would
                // fall back to the global image loader's 160ms crossfade.
                crossfade(crossfadeEnabled)
                if (crossfadeEnabled) crossfade(200)
            }
            .placeholder(placeholder)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onError = { onError?.invoke() }
    )
}
