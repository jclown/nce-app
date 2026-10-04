package com.example.nce

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nce.ui.Dest
import com.example.nce.ui.bottomItems
import com.example.nce.ui.checkin.CheckInScreen
import com.example.nce.ui.lesson.LessonScreen
import com.example.nce.ui.review.ReviewScreen
import com.example.nce.ui.study.StudyHomeScreen
import com.example.nce.ui.theme.NceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NceTheme {
                MainScaffold()
            }
        }
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val showBottomBar = currentDest?.hierarchy?.any {
        it.route == Dest.Study.route || it.route == Dest.CheckIn.route
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Study.route,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(240)) { it / 12 } },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) + slideInHorizontally(tween(240)) { -it / 12 } },
            popExitTransition = { fadeOut(tween(120)) + slideOutHorizontally(tween(240)) { it / 12 } },
        ) {
            composable(Dest.Study.route) { StudyHomeScreen(navController) }
            composable(Dest.CheckIn.route) { CheckInScreen() }
            composable(
                Dest.Lesson.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.LongType }),
            ) { entry ->
                LessonScreen(
                    lessonId = entry.arguments?.getLong("lessonId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onReview = { id -> navController.navigate(Dest.Review.of(id)) },
                )
            }
            composable(
                Dest.Review.route,
                arguments = listOf(navArgument("lessonId") { type = NavType.LongType }),
            ) { entry ->
                ReviewScreen(
                    lessonId = entry.arguments?.getLong("lessonId") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
