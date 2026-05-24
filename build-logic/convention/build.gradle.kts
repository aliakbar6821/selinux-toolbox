plugins {
    `kotlin-dsl`
}

group = "com.selinuxtoolbox.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.plugins.android.application.get().let {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    })
    compileOnly(libs.plugins.android.library.get().let {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    })
    compileOnly(libs.plugins.kotlin.android.get().let {
        "org.jetbrains.kotlin:kotlin-gradle-plugin:${it.version}"
    })
    compileOnly(libs.plugins.hilt.get().let {
        "com.google.dagger:hilt-android-gradle-plugin:${it.version}"
    })
    compileOnly(libs.plugins.ksp.get().let {
        "com.google.devtools.ksp:symbol-processing-gradle-plugin:${it.version}"
    })
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "selinuxtoolbox.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "selinuxtoolbox.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "selinuxtoolbox.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "selinuxtoolbox.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "selinuxtoolbox.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
    }
}
