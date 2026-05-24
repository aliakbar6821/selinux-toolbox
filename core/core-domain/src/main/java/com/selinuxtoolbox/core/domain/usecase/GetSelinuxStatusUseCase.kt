package com.selinuxtoolbox.core.domain.usecase

import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.model.DeviceInfo
import com.selinuxtoolbox.core.model.SelinuxMode
import javax.inject.Inject

data class SelinuxStatus(
    val mode: SelinuxMode,
    val deviceModel: String,
    val androidVersion: String,
    val vendorApiLevel: Int
)

class GetSelinuxStatusUseCase @Inject constructor(
    private val rootShell: RootShell
) {
    suspend operator fun invoke(): SelinuxStatus {
        val mode = rootShell.getSelinuxMode()
        val model = rootShell.getDeviceModel()
        val version = rootShell.getAndroidVersion()
        val vendorApi = rootShell.getVendorApiLevel()
        return SelinuxStatus(
            mode = mode,
            deviceModel = model,
            androidVersion = version,
            vendorApiLevel = vendorApi
        )
    }
}
