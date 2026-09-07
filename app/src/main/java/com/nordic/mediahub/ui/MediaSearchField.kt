package com.nordic.mediahub.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.nordic.mediahub.ui.theme.NordicControlSizes
import com.nordic.mediahub.ui.theme.NordicShapes

/** Search and local filtering share typography, feedback and keyboard behavior; data remains caller-owned. */
@Composable
internal fun MediaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    onClear: () -> Unit,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    showClear: Boolean = value.isNotBlank()
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colorScheme.onSurfaceVariant)
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant) },
        trailingIcon = if (showClear) {
            {
                AnimatedIconButton(
                    Icons.Filled.Close, clearDescription, onClear,
                    colorScheme = colorScheme, containerColor = Color.Transparent,
                    iconSize = NordicControlSizes.compactIcon
                )
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.outline,
            focusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.42f),
            focusedTextColor = colorScheme.onSurface,
            unfocusedTextColor = colorScheme.onSurface,
            cursorColor = colorScheme.primary
        ),
        shape = NordicShapes.md,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            focus.clearFocus()
        })
    )
}
