pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val packagesUser = providers.gradleProperty("vbotPackagesUser")
    .orElse(providers.environmentVariable("VBOT_PACKAGES_USER"))
val packagesToken = providers.gradleProperty("vbotPackagesToken")
    .orElse(providers.environmentVariable("VBOT_PACKAGES_TOKEN"))

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "VitaDynamicsPackages"
                    url = uri("https://maven.pkg.github.com/vitadynamics/vbot-android-sdk-demo")
                    credentials {
                        username = packagesUser.orNull
                        password = packagesToken.orNull
                    }
                }
            }
            filter { includeModule("com.vita.vbot", "vbot-sdk") }
        }
    }
}

rootProject.name = "VBotAndroidSdkDemo"
include(":demo")
