package com.nordic.mediahub.ui

import android.media.audiofx.Equalizer
import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicSpacing
import java.util.Locale

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

    MusicEqualizerContent(
        available = equalizer != null, presetNames = presetNames, bandCount = bandCount,
        bandLevelRange = bandLevelRange, centerFreqs = centerFreqs, bandLevels = bandLevels,
        selectedPreset = selectedPreset, colorScheme = colorScheme, onDismiss = onDismiss,
        onSelectPreset = { index ->
            try {
                equalizer?.usePreset(index.toShort())
                selectedPreset = index
                bandLevels = (0 until bandCount).map { equalizer?.getBandLevel(it.toShort()) ?: 0 }
            } catch (e: Exception) {
                Log.e("MusicEqualizer", "Failed to apply equalizer preset", e)
            }
        },
        onBandLevelChange = { band, level ->
            try {
                equalizer?.setBandLevel(band.toShort(), level)
                selectedPreset = -1
                bandLevels = bandLevels.toMutableList().also { it[band] = level }
            } catch (e: Exception) {
                Log.e("MusicEqualizer", "Failed to set equalizer band level", e)
            }
        }
    )
}

/** No audio effect, service, or preferences are created by this content layer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicEqualizerContent(
    available: Boolean,
    presetNames: List<String>,
    bandCount: Int,
    bandLevelRange: Pair<Int, Int>,
    centerFreqs: List<Int>,
    bandLevels: List<Short>,
    selectedPreset: Int,
    colorScheme: ColorScheme,
    onDismiss: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandLevelChange: (Int, Short) -> Unit
) {
    MediaPlayerSheet("均衡器", colorScheme, onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(NordicSpacing.lg)) {
            if (!available) {
                item { MusicDetailEmptyState("均衡器不可用", "此音频会话暂不支持调整音效。") }
            } else {
                if (presetNames.isNotEmpty()) item(key = "presets") {
                    Column(verticalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                        Text("预设", style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() })
                        LazyRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(NordicSpacing.sm)) {
                            itemsIndexed(presetNames, key = { index, name -> "eq-preset-$name-$index" }) { index, name ->
                                MediaChoiceChip(name, selectedPreset == index, colorScheme,
                                    role = Role.RadioButton, onClick = { onSelectPreset(index) })
                            }
                        }
                    }
                }
                if (bandCount > 0) item(key = "band-heading") {
                    Text("自定义频段", style = MaterialTheme.typography.titleSmall, color = colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() })
                }
                items(bandCount.coerceAtLeast(0), key = { "eq-band-$it" }) { band ->
                    val frequency = equalizerFrequencyLabel(centerFreqs.getOrElse(band) { 0 })
                    val level = bandLevels.getOrElse(band) { 0 }
                    val levelLabel = String.format(Locale.getDefault(), "%.1f dB", level / 100f)
                    val (min, max) = bandLevelRange
                    val adjustable = min < max
                    val interactionSource = remember { MutableInteractionSource() }
                    val sliderColors = SliderDefaults.colors(thumbColor = colorScheme.primary, activeTrackColor = colorScheme.primary)
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(frequency, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                            Text(levelLabel, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = colorScheme.onSurfaceVariant)
                        }
                        Slider(
                            value = if (adjustable) level.toInt().coerceIn(min, max).toFloat() else 0f,
                            onValueChange = { onBandLevelChange(band, it.toInt().toShort()) },
                            valueRange = if (adjustable) min.toFloat()..max.toFloat() else 0f..1f,
                            enabled = adjustable,
                            // Material3 1.3 expands slider semantics 10dp on either side. Reserve real
                            // space inside the lazy viewport; the stock 44dp thumb is also below our 48dp target.
                            modifier = Modifier.fillMaxWidth().padding(horizontal = NordicSpacing.md)
                                .heightIn(min = NordicControlSizes.touchTarget).semantics {
                                contentDescription = "调整 $frequency 频段"
                                stateDescription = levelLabel
                            },
                            colors = sliderColors,
                            interactionSource = interactionSource,
                            thumb = {
                                SliderDefaults.Thumb(interactionSource, colors = sliderColors, enabled = adjustable,
                                    thumbSize = DpSize(4.dp, NordicControlSizes.touchTarget))
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun equalizerFrequencyLabel(milliHertz: Int): String {
    val hertz = milliHertz.coerceAtLeast(0) / 1000
    return if (hertz < 1000) "$hertz Hz" else String.format(Locale.getDefault(), "%.1f kHz", hertz / 1000f)
}
