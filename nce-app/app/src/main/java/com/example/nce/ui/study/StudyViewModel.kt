package com.example.nce.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nce.data.asset.BookImporter
import com.example.nce.data.asset.ImportState
import com.example.nce.data.db.Book
import com.example.nce.data.db.BookDao
import com.example.nce.data.db.LessonCard
import com.example.nce.data.db.LessonDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StudyUiState(
    val books: List<Book> = emptyList(),
    val selectedBookId: Int = 1,
    val cards: List<LessonCard> = emptyList(),
    val importState: ImportState = ImportState.Idle,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StudyViewModel @Inject constructor(
    bookDao: BookDao,
    lessonDao: LessonDao,
    importer: BookImporter,
) : ViewModel() {

    private val selectedBookId = MutableStateFlow(1)

    val uiState: StateFlow<StudyUiState> = combine(
        bookDao.all(),
        selectedBookId,
        selectedBookId.flatMapLatest { lessonDao.cardsOfBook(it) },
        importer.state,
    ) { books, bookId, cards, importState ->
        StudyUiState(
            books = books,
            selectedBookId = bookId,
            cards = cards,
            importState = importState,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StudyUiState())

    fun selectBook(id: Int) { selectedBookId.value = id }
}
