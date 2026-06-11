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
import androidx.compose.ui.draw.clip
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
        color = Surface,
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
        Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 24.sp)
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) Primary else TextSecondary
        )
    }
}

@Composable
fun NowPlayingBar() {
    Surface(
        color = Surface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("播放队列", fontSize = 15.sp, color = TextPrimary)
                Text("等待播放", fontSize = 13.sp, color = TextSecondary)
            }
            Text("⏸", fontSize = 28.sp)
        }
    }
}

@Composable
fun MusicScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "音乐库",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(5) {
            TrackCard("歌曲 ${it + 1}", "艺术家", "04:3$it")
        }
    }
}

@Composable
fun AudiobookScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "有声书",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(3) {
            AudiobookCard("书名 ${it + 1}", "作者", "12章")
        }
    }
}

@Composable
fun VideoScreen() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "视频",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(4) {
            VideoCard("视频 ${it + 1}", "2小时3${it}分")
        }
    }
}

@Composable
fun TrackCard(title: String, artist: String, duration: String) {
    Surface(
        color = Surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = TextPrimary)
                Text(artist, fontSize = 13.sp, color = TextSecondary)
            }
            Text(duration, fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
fun AudiobookCard(title: String, author: String, chapters: String) {
    Surface(
        color = Surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(author, fontSize = 13.sp, color = TextSecondary)
                Text(chapters, fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun VideoCard(title: String, duration: String) {
    Surface(
        color = Surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(180.dp)
                    .background(Color.LightGray)
            )
            Column(Modifier.padding(12.dp)) {
                Text(title, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(duration, fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}
