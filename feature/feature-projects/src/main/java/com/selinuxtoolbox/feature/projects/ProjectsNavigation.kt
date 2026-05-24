package com.selinuxtoolbox.feature.projects

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val PROJECTS_ROUTE = "projects"
const val PROJECT_DETAIL_ROUTE = "project_detail"
const val CREATE_PROJECT_ROUTE = "create_project"
const val PROJECT_ID_ARG = "projectId"

fun NavController.navigateToProjects() = navigate(PROJECTS_ROUTE)
fun NavController.navigateToCreateProject() = navigate(CREATE_PROJECT_ROUTE)
fun NavController.navigateToProjectDetail(projectId: Long) =
    navigate("$PROJECT_DETAIL_ROUTE/$projectId")

fun NavGraphBuilder.projectsGraph(
    navController: NavController
) {
    composable(PROJECTS_ROUTE) {
        ProjectListScreen(navController = navController)
    }
    composable(CREATE_PROJECT_ROUTE) {
        CreateProjectScreen(navController = navController)
    }
    composable(
        route = "$PROJECT_DETAIL_ROUTE/{$PROJECT_ID_ARG}",
        arguments = listOf(navArgument(PROJECT_ID_ARG) { type = NavType.LongType })
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getLong(PROJECT_ID_ARG) ?: return@composable
        ProjectDetailScreen(
            projectId = projectId,
            navController = navController
        )
    }
}
