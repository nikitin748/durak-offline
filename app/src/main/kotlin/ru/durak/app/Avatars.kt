package ru.durak.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AVATARS = listOf("😺", "🐶", "🦊", "🐻", "🐼", "🐵", "🐸", "🦁", "🐯", "🐰", "🐧", "🦉")
val AVATAR_BG = listOf(0xFFF4B942, 0xFF8FC1E3, 0xFFF08A5D, 0xFFB5838D, 0xFFB8E0D2, 0xFFD6A77A, 0xFF9BC53D, 0xFFF9C74F, 0xFFF3722C, 0xFFE5B8F4, 0xFF90BEDE, 0xFFC9ADA7).map { Color(it) }

val STICKERS = listOf("😂", "👍", "😡", "😱", "🤔", "😎", "🥳", "😭", "🙏", "👏", "🔥", "💩")

@Composable
fun Avatar(index: Int, size: Dp, ring: Color? = null, modifier: Modifier = Modifier) {
    val i = index.mod(AVATARS.size)
    Box(
        modifier.size(size).clip(CircleShape).background(AVATAR_BG[i])
            .border(if (ring != null) 3.dp else 1.5.dp, ring ?: Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(AVATARS[i], fontSize = (size.value * 0.56f).sp) }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Pal.Red) {
    Box(modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp).clip(CircleShape).background(color).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
