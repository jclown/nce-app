package com.example.nce.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.nce.data.asset.ImportState
import com.example.nce.data.db.LessonCard
import com.example.nce.ui.Dest

@Composable
fun StudyHomeScreen(navController: NavController, viewModel: StudyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // 册别选择
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.books.forEach { book ->
                FilterChip(
                    selected = state.selectedBookId == book.id,
                    onClick = { viewModel.selectBook(book.id) },
                    label = { Text(book.shortName) },
                )
            }
        }

        ImportBanner(state.importState)

        if (state.cards.isEmpty() && state.importState is ImportState.Running) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在导入课文…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.cards, key = { it.id }) { card ->
                    LessonCardView(card) { navController.navigate(Dest.Lesson.of(card.id)) }
                }
            }
        }
    }
}

@Composable
private fun ImportBanner(importState: ImportState) {
    when (importState) {
        is ImportState.Running -> Column(Modifier.padding(horizontal = 16.dp)) {
            LinearProgressIndicator(
                progress = { importState.done / importState.total.toFloat().coerceAtLeast(1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "导入 ${importState.bookShort}：${importState.done}/${importState.total}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is ImportState.Failed -> Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Text("导入失败（${importState.bookShort}）：${importState.message}",
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer)
        }
        else -> Unit
    }
}

@Composable
private fun LessonCardView(card: LessonCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        onClick = onClick,
        shape = shape,
        // 阴影在转场期间每帧重绘开销大，改用描边
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            Column {
                Text(
                    card.lessonNoLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.completed) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                )
            }
        }
    }
}
