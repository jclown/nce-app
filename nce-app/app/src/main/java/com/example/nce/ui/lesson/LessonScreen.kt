package com.example.nce.ui.lesson

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nce.data.db.Sentence
import com.example.nce.data.repo.LanguageMode

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    lessonId: Long,
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    viewModel: LessonViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 从复习页返回时重新装载共享播放器
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.ensureLoaded()
    }

    // 当前播放句自动滚动到可视区
    LaunchedEffect(state.playState.currentIndex) {
        val idx = state.playState.currentIndex
        if (idx >= 0) runCatching { listState.animateScrollToItem(idx, scrollOffset = -80) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.lesson?.lessonNoLabel ?: "课文", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.lesson?.title ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 语言切换
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LangChip("EN", LanguageMode.EN, state.languageMode) { viewModel.setLanguageMode(it) }
                LangChip("EN+CN", LanguageMode.EN_CN, state.languageMode) { viewModel.setLanguageMode(it) }
                LangChip("CN", LanguageMode.CN, state.languageMode) { viewModel.setLanguageMode(it) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.sentences, key = { _, s -> s.index }) { i, s ->
                    SentenceCard(
                        sentence = s,
                        mode = state.languageMode,
                        isCurrent = state.playState.currentIndex == i,
                        isPlaying = state.playState.currentIndex == i && state.playState.isPlaying,
                        onClick = { viewModel.toggleSentence(i) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.playState.autoPlay,
                    onClick = { viewModel.setAutoPlay(!state.playState.autoPlay) },
                    label = { Text("自动连播") },
                )
                if (state.progress?.completed != true) {
                    OutlinedButton(
                        onClick = { viewModel.completeLesson() },
                        modifier = Modifier.weight(1f),
                    ) { Text("完成本课") }
                }
                Button(
                    onClick = { onReview(lessonId) },
                    modifier = Modifier.weight(1f),
                ) { Text("复习") }
            }
        }
    }
}

@Composable
private fun LangChip(label: String, mode: LanguageMode, current: LanguageMode, onSelect: (LanguageMode) -> Unit) {
    FilterChip(selected = current == mode, onClick = { onSelect(mode) }, label = { Text(label) })
}

@Composable
private fun SentenceCard(
    sentence: Sentence,
    mode: LanguageMode,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (isCurrent) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = "播放中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        "${sentence.index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (mode != LanguageMode.CN) {
                    Text(
                        sentence.en,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (mode != LanguageMode.EN && !sentence.cn.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        sentence.cn!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
