package com.nordic.mediahub.ui

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
    onError: (() -> Unit)? = null
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    val imageRequest = remember(url) {
        val cleanCacheKey = stripAuthQuery(url)
        ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(cleanCacheKey)
            .memoryCacheKey(cleanCacheKey)
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
