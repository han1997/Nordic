package com.nordic.mediahub.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nordic.mediahub.data.ConfigRepository
import com.nordic.mediahub.data.VideoServerConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun VideoScreen(colorScheme: ColorScheme, isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val repository = remember { ConfigRepository(context) }
    val savedConfig by repository.videoConfig.collectAsStateWithLifecycle(VideoServerConfig())
    var config by remember { mutableStateOf(VideoServerConfig()) }
    var showConfig by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(savedConfig) { config = savedConfig }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "视频",
                    modifier = Modifier.weight(1f),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HeaderActionGroup(
                    actions = listOf(
                        HeaderAction(if (isDark) "☀" else "☾") { onThemeToggle(!isDark) },
                        HeaderAction("⚙") { showConfig = !showConfig }
                    )
                )
            }
        }
        item {
            AnimatedVisibility(
                visible = showConfig,
                enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                VideoConfigCard(config, colorScheme,
                    onConfigChange = { config = it },
                    onSave = { scope.launch { repository.saveVideoConfig(config) } }
                )
            }
        }
        itemsIndexed(listOf("视频 1", "视频 2", "视频 3", "视频 4")) { index, title ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 50L)
                visible = true
            }
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically { it / 2 }
            ) {
                VideoCard(title, "2小时3${index}分", colorScheme)
            }
        }
    }
}

@Composable
fun VideoCard(title: String, duration: String, colorScheme: ColorScheme) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    Surface(
        color = colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource, null) {}
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .background(Brush.linearGradient(listOf(colorScheme.secondary.copy(0.3f), colorScheme.primary.copy(0.3f))))
            )
            Column(Modifier.padding(14.dp)) {
                Text(title, fontSize = 15.sp, color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(duration, fontSize = 13.sp, color = colorScheme.onSurface.copy(0.6f))
            }
        }
    }
}
