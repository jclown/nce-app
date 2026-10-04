package com.example.nce.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nce.data.repo.CheckInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class CheckInUiState(
    val streak: Int = 0,
    val todayCount: Int = 0,
    val month: YearMonth = YearMonth.now(),
    val monthMarks: Map<Long, Int> = emptyMap(),
    val today: Long = LocalDate.now().toEpochDay(),
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val repo: CheckInRepository,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CheckInUiState> = combine(
        repo.observeAll(),
        month,
    ) { all, m ->
        val info = repo.streakInfoOf(all)
        CheckInUiState(
            streak = info.streak,
            todayCount = info.todayCount,
            month = m,
            monthMarks = repo.monthMarksOf(all, m),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CheckInUiState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
}
