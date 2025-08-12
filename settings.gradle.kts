import java.io.FileInputStream
import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven {
            url = uri("https://maven.pkg.github.com/synonymdev/bitkit-core")
            credentials {

                val localPropertiesFile = File(rootDir, "gradle.properties")
                val localProperties = Properties()

                if (localPropertiesFile.exists()) {
                    localProperties.load(FileInputStream(localPropertiesFile))
                }

                username = System.getenv("GITHUB_ACTOR")
                    ?: localProperties.getProperty("gpr.user")
                        ?: providers.gradleProperty("gpr.user").orNull


                password = System.getenv("GITHUB_TOKEN")
                    ?: localProperties.getProperty("gpr.key")
                        ?: providers.gradleProperty("gpr.key").orNull
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/synonymdev/vss-rust-client-ffi")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
rootProject.name = "bitkit-android"
include(":app")
