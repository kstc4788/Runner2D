package com.example.runner2d.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RunnerColorScheme = darkColorScheme(
    primary = MintPlayer,
    secondary = CoralObstacle,
    background = NavyDark,
    surface = NavyMid,
)

@Composable
fun Runner2DTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RunnerColorScheme,
        typography = Typography,
        content = content,
    )
}
