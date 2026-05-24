package com.selinuxtoolbox.core.model

import kotlinx.serialization.Serializable

// Generic UI state wrapper
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>()
}

// Root access status
sealed class RootStatus {
    object Checking : RootStatus()
    object Available : RootStatus()
    object NotAvailable : RootStatus()
    data class Error(val message: String) : RootStatus()
}

// Progress for long-running operations
@Serializable
data class OperationProgress(
    val current: Int,
    val total: Int,
    val currentFile: String = "",
    val message: String = ""
) {
    val percentage: Float get() = if (total > 0) current.toFloat() / total else 0f
}

// Dashboard summary data
@Serializable
data class DashboardData(
    val selinuxMode: SelinuxMode,
    val recentDenials: List<AvcDenial>,
    val activeProject: Project?,
    val deviceInfo: DeviceInfo?,
    val policyLoadedAt: Long?
)

// For the live denial stream
sealed class DenialStreamEvent {
    data class NewDenial(val denial: AvcDenial) : DenialStreamEvent()
    data class Error(val message: String) : DenialStreamEvent()
    object StreamEnded : DenialStreamEvent()
}
