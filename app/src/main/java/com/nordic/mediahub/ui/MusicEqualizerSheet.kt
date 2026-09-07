package com.nordic.mediahub.ui

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nordic.mediahub.ui.theme.NordicAlpha
import com.nordic.mediahub.ui.theme.NordicShapes
import com.nordic.mediahub.ui.theme.NordicSpacing
import kotlin.math.roundToInt

@Composable
fun MusicEqualizerSheet(
    audioSessionId: Int,
    colorScheme: androidx.compose.material3.ColorScheme,
    onDismiss: () -> Unit
) {
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var presetNames by remember { mutableStateOf(emptyList<String>()) }
    var bandCount by remember { mutableIntStateOf(0) }
    var bandLevelRange by remember { mutableStateOf(0 to 0) }
    var centerFreqs by remember { mutableStateOf(emptyList<Int>()) }
    var bandLevels by remember { mutableStateOf(emptyList<Short>()) }
    var selectedPreset by remember { mutableIntStateOf(-1) }

    DisposableEffect(audioSessionId) {
        val eq = try {
            Equalizer(0, audioSessionId)
        } catch (e: Exception) {
            Log.e("MusicEqualizer", "Failed to create equalizer", e)
            null
        }
        equalizer = eq
        if (eq != null) {
            eq.enabled = true
            presetNames = (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }
            bandCount = eq.numberOfBands.toInt()
            bandLevelRange = eq.bandLevelRange[0].toInt() to eq.bandLevelRange[1].toInt()
            centerFreqs = (0 until eq.numberOfBands.toInt()).map { eq.getCenterFreq(it.toShort()).toInt() }
            bandLevels = (0 until eq.numberOfBands.toInt()).map { eq.getBandLevel(it.toShort()) }
            selectedPreset = -1
        }

        onDispose {
            eq?.release()
            equalizer = null
        }
    }

    MediaPlayerSheet(
        title = "均衡器",
        colors = colorScheme,
        onDismiss = onDismiss,
        skipPartiallyExpanded = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)
        ) {

            if (equalizer == null) {
                Surface(
                    color = colorScheme.surfaceVariant.copy(alpha = 0.46f),
                    shape = NordicShapes.md,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NordicSpacing.md)
                ) {
                    Text(
                        "均衡器不可用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = NordicSpacing.lg, vertical = NordicSpacing.xxl)
                    )
                }
            } else {
                // Preset buttons
                if (presetNames.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(horizontal = NordicSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                    ) {
                        Text(
                            "预设",
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                            modifier = Modifier.padding(horizontal = NordicSpacing.sm)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                        ) {
                            itemsIndexed(
                                items = presetNames,
                                key = { index, name -> "eq-preset-$name-$index" }
                            ) { index, name ->
                                val isSelected = selectedPreset == index
                                Surface(
                                    color = if (isSelected) {
                                        colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        colorScheme.surfaceVariant.copy(alpha = 0.42f)
                                    },
                                    contentColor = if (isSelected) {
                                        colorScheme.primary
                                    } else {
                                        colorScheme.onSurface.copy(alpha = NordicAlpha.medium)
                                    },
                                    shape = NordicShapes.full,
                                    modifier = Modifier.clickable {
                                        try {
                                            equalizer?.usePreset(index.toShort())
                                            selectedPreset = index
                                            bandLevels = (0 until bandCount).map {
                                                equalizer?.getBandLevel(it.toShort()) ?: 0
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MusicEqualizer", "Failed to apply equalizer preset", e)
                                        }
                                    }
                                ) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = NordicSpacing.md, vertical = NordicSpacing.sm)
                                    )
                                }
                            }
                        }
                    }
                }

                // Band sliders - horizontal sliders labeled with frequency
                if (bandCount > 0) {
                    Column(
                        modifier = Modifier.padding(horizontal = NordicSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                    ) {
                        Text(
                            "自定义频段",
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface.copy(alpha = NordicAlpha.medium),
                            modifier = Modifier.padding(horizontal = NordicSpacing.sm)
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = NordicSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(NordicSpacing.xs)
                        ) {
                            for (band in 0 until bandCount) {
                                val level = bandLevels.getOrElse(band) { 0 }
                                val freqHz = centerFreqs.getOrElse(band) { 0 } / 1000
                                val freqLabel = if (freqHz >= 1000) {
                                    "${freqHz / 1000}kHz"
                                } else {
                                    "${freqHz}Hz"
                                }
                                val minLevel = bandLevelRange.first
                                val maxLevel = bandLevelRange.second

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)
                                ) {
                                    Text(
                                        freqLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                                        modifier = Modifier.width(52.dp)
                                    )
                                    Slider(
                                        value = level.toFloat(),
                                        onValueChange = { newLevel ->
                                            try {
                                                equalizer?.setBandLevel(
                                                    band.toShort(),
                                                    newLevel.toInt().toShort()
                                                )
                                                selectedPreset = -1
                                                bandLevels = bandLevels.toMutableList().also {
                                                    it[band] = newLevel.toInt().toShort()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("MusicEqualizer", "Failed to set equalizer band level", e)
                                            }
                                        },
                                        valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = colorScheme.primary,
                                            activeTrackColor = colorScheme.primary,
                                            inactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.13f)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    val dbLabel = String.format("%.1f", level.toFloat() / 100f)
                                    Text(
                                        "${dbLabel}dB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface.copy(alpha = NordicAlpha.subtle),
                                        modifier = Modifier.width(48.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
