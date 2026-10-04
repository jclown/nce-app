package com.example.nce.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun CheckInScreen(viewModel: CheckInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 连胜卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "${state.streak} 天连胜",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        "今日已完成 ${state.todayCount} 课",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 月份切换
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::previousMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                state.month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = viewModel::nextMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }

        Spacer(Modifier.height(8.dp))

        // 星期表头（周一为首列）
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().height(24.dp),
            userScrollEnabled = false,
        ) {
            items(weekdays) {
                Text(
                    it,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 日历网格
        val cells = buildCalendarCells(state.month)
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = false,
        ) {
            items(cells) { cell ->
                when (cell) {
                    is CalCell.Blank -> Box(Modifier.aspectRatio(1f))
                    is CalCell.Day -> DayCell(
                        day = cell.day,
                        doneCount = state.monthMarks[cell.epochDay] ?: 0,
                        isToday = cell.epochDay == state.today,
                    )
                }
            }
        }
    }
}

private sealed interface CalCell {
    data object Blank : CalCell
    data class Day(val day: Int, val epochDay: Long) : CalCell
}

private fun buildCalendarCells(ym: YearMonth): List<CalCell> {
    val first = ym.atDay(1)
    // 周一为首列：Mon=1..Sun=7 -> offset 0..6
    val offset = (first.dayOfWeek.value + 6) % 7
    val cells = mutableListOf<CalCell>()
    repeat(offset) { cells += CalCell.Blank }
    for (d in 1..ym.lengthOfMonth()) {
        cells += CalCell.Day(d, ym.atDay(d).toEpochDay())
    }
    return cells
}

@Composable
private fun DayCell(day: Int, doneCount: Int, isToday: Boolean) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        val hasDone = doneCount > 0
        val bg = if (hasDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val contentColor = if (hasDone) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bg)
                .then(
                    if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$day",
                color = contentColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (doneCount > 1) {
                Text(
                    "$doneCount",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
