package com.nordic.mediahub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nordic.mediahub.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NordicTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            Column {
                NowPlayingBar()
                BottomNav(selectedTab) { selectedTab = it }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> MusicScreen()
                1 -> AudiobookScreen()
                2 -> VideoScreen()
            }
        }
    }
}

@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        color = Surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavItem("🎵", "音乐", selected == 0) { onSelect(0) }
            NavItem("📚", "有声书", selected == 1) { onSelect(1) }
            NavItem("📺", "视频", selected == 2) { onSelect(2) }
        }
    }
}

@Composable
fun NavItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 24.sp)
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) Primary else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun NowPlayingBar() {
    Surface(
        color = Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Primary.copy(0.4f), Accent.copy(0.4f))))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("播放队列", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text("等待播放", fontSize = 13.sp, color = TextSecondary)
            }
            Text("⏸", fontSize = 28.sp, color = Primary)
        }
    }
}

@Composable
fun MusicScreen() {
    var serverUrl by remember { mutableStateOf("") }
    var showConfig by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("音乐库", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = { showConfig = !showConfig }) {
                    Text("⚙", fontSize = 24.sp)
                }
            }
        }
        if (showConfig) {
            item {
                ServerConfigCard(
                    label = "Navidrome 服务器",
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = "https://music.example.com"
                )
            }
        }
        items(5) {
            TrackCard("歌曲 ${it + 1}", "艺术家", "04:3$it")
        }
    }
}

@Composable
fun AudiobookScreen() {
    var serverUrl by remember { mutableStateOf("") }
    var showConfig by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("有声书", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = { showConfig = !showConfig }) {
                    Text("⚙", fontSize = 24.sp)
                }
            }
        }
        if (showConfig) {
            item {
                ServerConfigCard(
                    label = "AudiobookShelf 服务器",
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = "https://audiobook.example.com"
                )
            }
        }
        items(3) {
            AudiobookCard("书名 ${it + 1}", "作者", "12章")
        }
    }
}

@Composable
fun VideoScreen() {
    var serverType by remember { mutableStateOf("Emby") }
    var serverUrl by remember { mutableStateOf("") }
    var showConfig by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("视频", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                IconButton(onClick = { showConfig = !showConfig }) {
                    Text("⚙", fontSize = 24.sp)
                }
            }
        }
        if (showConfig) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Emby", "Plex", "WebDAV").forEach { type ->
                            Surface(
                                color = if (serverType == type) Primary else SurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).clickable { serverType = type }
                            ) {
                                Text(
                                    type,
                                    modifier = Modifier.padding(12.dp),
                                    color = if (serverType == type) Background else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    ServerConfigCard(
                        label = "$serverType 服务器",
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        placeholder = "https://video.example.com"
                    )
                }
            }
        }
        items(4) {
            VideoCard("视频 ${it + 1}", "2小时3${it}分")
        }
    }
}

@Composable
fun TrackCard(title: String, artist: String, duration: String) {
    Surface(
        color = SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Primary.copy(0.3f), Secondary.copy(0.3f))))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(artist, fontSize = 13.sp, color = TextSecondary)
            }
            Text(duration, fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
fun AudiobookCard(title: String, author: String, chapters: String) {
    Surface(
        color = SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Accent.copy(0.3f), Primary.copy(0.3f))))
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(author, fontSize = 13.sp, color = TextSecondary)
                Text(chapters, fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun VideoCard(title: String, duration: String) {
    Surface(
        color = SurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .background(Brush.linearGradient(listOf(Secondary.copy(0.3f), Primary.copy(0.3f))))
            )
            Column(Modifier.padding(14.dp)) {
                Text(title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(duration, fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ServerConfigCard(label: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Surface(
        color = SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = TextSecondary.copy(0.5f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = TextSecondary.copy(0.3f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
