plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.extendedclip.com/releases/")
        maven("https://maven.maxhenkel.de/repository/public")
    }
}

rootProject.name = "smthsVanish"

include("smthsVanish-common")
include("smthsVanish-paper")
include("smthsVanish-velocity")
