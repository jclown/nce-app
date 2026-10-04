package com.example.nce.ui.review

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nce.data.db.Sentence
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    lessonId: Long,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pendingIndex by remember { mutableStateOf(-1) }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingIndex >= 0) {
            viewModel.startRecording(pendingIndex)
        } else if (!granted) {
            scope.launch { snackbar.showSnackbar("需要麦克风权限才能录音") }
        }
        pendingIndex = -1
    }

    val requestRecord: (Int) -> Unit = { index ->
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording(index)
        else {
            pendingIndex = index
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("复习 · 逐句录音") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopRecording()
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val done = state.recordings.size
            val total = state.sentences.size
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                "已完成 $done / $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.sentences, key = { it.index }) { s ->
                    ReviewSentenceRow(
                        sentence = s,
                        recorded = state.recordings.containsKey(s.index),
                        isRecording = state.recordingIndex == s.index,
                        isPlaying = state.playingIndex == s.index,
                        isOriginalPlaying = state.originalIndex == s.index && state.originalPlaying,
                        isOriginalCurrent = state.originalIndex == s.index,
                        elapsedMs = state.elapsedMs,
                        onToggleOriginal = { viewModel.toggleOriginal(s.index) },
                        onRequestRecord = { requestRecord(s.index) },
                        onStopRecord = { viewModel.stopRecording() },
                        onPlay = { viewModel.playRecording(s.index) },
                        onDelete = { viewModel.deleteRecording(s.index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewSentenceRow(
    sentence: Sentence,
    recorded: Boolean,
    isRecording: Boolean,
    isPlaying: Boolean,
    isOriginalPlaying: Boolean,
    isOriginalCurrent: Boolean,
    elapsedMs: Long,
    onToggleOriginal: () -> Unit,
    onRequestRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRecording -> MaterialTheme.colorScheme.primaryContainer
                isOriginalCurrent -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 点击文本区播放/暂停原音
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onToggleOriginal),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOriginalPlaying) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = "原音播放中",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        sentence.en,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (!sentence.cn.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sentence.cn!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isRecording) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "录音中 ${(elapsedMs / 1000f)}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (isOriginalCurrent && !isOriginalPlaying) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "原音已暂停，点击继续",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            when {
                isRecording -> FilledIconButton(
                    onClick = onStopRecord,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Icon(Icons.Filled.Stop, contentDescription = "停止录音") }

                recorded -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPlay) {
                        Icon(
                            if (isPlaying) Icons.Filled.Check else Icons.Filled.PlayArrow,
                            contentDescription = "回放",
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                    FilledIconButton(
                        onClick = onRequestRecord,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Icon(Icons.Filled.Mic, contentDescription = "重录") }
                }

                else -> FilledIconButton(
                    onClick = onRequestRecord,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) { Icon(Icons.Filled.Mic, contentDescription = "录音") }
            }
        }
    }
}
