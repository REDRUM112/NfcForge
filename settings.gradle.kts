import java.io.File
import org.gradle.api.Project
import org.gradle.kotlin.dsl.closureOf

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Workaround for Windows/OneDrive "AccessDenied" issues when Gradle writes large intermediate trees
// under the project directory (e.g., Desktop synced by OneDrive).
//
// We redirect build output for ALL projects/modules to a user-profile folder outside OneDrive:
//   C:\Users\<you>\.nfcforge-build\<projectPath>\
//
// This keeps Android Studio + Gradle builds stable even in protected/synced folders.
val localBuildRoot = File(System.getProperty("user.home"), ".nfcforge-build")
gradle.beforeProject(
    closureOf<Project> {
        val safeName = path.trimStart(':').ifBlank { name }.replace(':', '_')
        layout.buildDirectory.set(File(localBuildRoot, safeName))
    },
)

rootProject.name = "NfcForge"
include(":app")



