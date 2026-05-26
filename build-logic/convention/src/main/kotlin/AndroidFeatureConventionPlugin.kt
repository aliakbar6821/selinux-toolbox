import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("selinuxtoolbox.android.library")
            pluginManager.apply("selinuxtoolbox.android.hilt")
            dependencies {
                add("implementation", project(":core:core-ui"))
                add("implementation", project(":core:core-model"))
                add("implementation", project(":core:core-domain"))
                add("implementation", project(":core:core-data"))
            }
        }
    }
}
