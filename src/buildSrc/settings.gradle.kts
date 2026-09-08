pluginManagement {
  repositories {
    fun getConfig(name: String): String? =
        System.getenv(name) ?: providers.gradleProperty(name).orNull

    val pluginsUrl = getConfig("XROAD_MIRROR_PLUGINS_URL")
    val username = getConfig("XROAD_MIRROR_USERNAME")
    val token = getConfig("XROAD_MIRROR_TOKEN")

    if (!pluginsUrl.isNullOrBlank() && !username.isNullOrBlank() && !token.isNullOrBlank()) {
      maven {
        name = "MirrorPlugins"
        url = uri(pluginsUrl)
        credentials {
          this.username = username
          password = token
        }
      }
    } else {
      gradlePluginPortal()
      mavenCentral()
    }
  }
}

rootProject.name = "x-road-gradle-plugins"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
  repositories {
    fun getConfig(name: String): String? =
        System.getenv(name) ?: providers.gradleProperty(name).orNull

    val mavenUrl = getConfig("XROAD_MIRROR_MAVEN_URL")
    val pluginsUrl = getConfig("XROAD_MIRROR_PLUGINS_URL")
    val username = getConfig("XROAD_MIRROR_USERNAME")
    val token = getConfig("XROAD_MIRROR_TOKEN")

    if (!mavenUrl.isNullOrBlank() && !username.isNullOrBlank() && !token.isNullOrBlank()) {
      maven {
        name = "MirrorMaven"
        url = uri(mavenUrl)
        credentials {
          this.username = username
          password = token
        }
      }
      if (!pluginsUrl.isNullOrBlank()) {
        maven {
          name = "MirrorPlugins"
          url = uri(pluginsUrl)
          credentials {
            this.username = username
            password = token
          }
        }
      }
    } else {
      mavenCentral()
      gradlePluginPortal()
    }
  }
}
