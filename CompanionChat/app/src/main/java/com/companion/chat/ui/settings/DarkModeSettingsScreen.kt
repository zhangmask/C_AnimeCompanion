package com.companion.chat.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.companion.chat.ui.theme.BrandOutlineVariant
import com.companion.chat.ui.theme.BrandPrimary
import com.companion.chat.ui.theme.BrandPrimaryContainer

private const val PREFS_NAME = "app_settings"
private const val KEY_DARK_MODE = "dark_mode" // "system", "light", "dark"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkModeSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var selectedMode by remember { mutableStateOf(prefs.getString(KEY_DARK_MODE, "system") ?: "system") }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("深色模式") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val options = listOf(
                "system" to "跟随系统",
                "light" to "浅色模式",
                "dark" to "深色模式"
            )
            options.forEach { (value, label) ->
                DarkModeOption(
                    label = label,
                    isSelected = selectedMode == value,
                    onClick = {
                        selectedMode = value
                        prefs.edit().putString(KEY_DARK_MODE, value).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun DarkModeOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) BrandPrimaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .then(
                if (isSelected) Modifier.border(1.dp, BrandPrimary, RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, BrandOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) BrandPrimary else MaterialTheme.colorScheme.onSurface
        )
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = BrandPrimary,
                unselectedColor = BrandOutlineVariant
            )
        )
    }
}
