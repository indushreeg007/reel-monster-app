package com.reelmonster.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Lavender = Color(0xFFCDB4DB)
val Pink = Color(0xFFFFC8DD)
val SoftPurple = Color(0xFFB79CED)
val Cream = Color(0xFFFFF8EE)
val PastelBlue = Color(0xFFBDE0FE)
val Peach = Color(0xFFFFD6A5)
val Ink = Color(0xFF4A3F5C)

@Composable
fun ReelTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = SoftPurple, onPrimary = Color.White, secondary = Pink, background = Cream,
        surface = Color.White, onBackground = Ink, onSurface = Ink, surfaceVariant = Lavender.copy(alpha = .3f)
    ),
    content = content
)

@Composable
fun SoftCard(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) =
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
    ) { Column(Modifier.padding(16.dp), content = content) }
