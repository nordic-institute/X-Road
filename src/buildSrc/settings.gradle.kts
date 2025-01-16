rootProject.name = "x-road-gradle-plugins"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}
