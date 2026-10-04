package com.example.nce.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Dest(val route: String) {
    data object Study : Dest("study")
    data object CheckIn : Dest("checkin")
    data object Lesson : Dest("lesson/{lessonId}") {
        fun of(id: Long) = "lesson/$id"
    }
    data object Review : Dest("review/{lessonId}") {
        fun of(id: Long) = "review/$id"
    }
}

data class BottomItem(val dest: Dest, val label: String, val icon: ImageVector)

val bottomItems = listOf(
    BottomItem(Dest.Study, "学习", Icons.Outlined.MenuBook),
    BottomItem(Dest.CheckIn, "打卡", Icons.Outlined.CalendarMonth),
)
