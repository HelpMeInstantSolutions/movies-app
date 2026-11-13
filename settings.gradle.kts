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
        maven {
            name = "DailymotionMavenRelease"
            url = java.net.URI.create("https://mvn.dailymotion.com/repository/releases/")
        }
    }
}

rootProject.name = "CourseApp"
include(":app")
