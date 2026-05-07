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

rootProject.name = "x-road-core"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    fun getConfig(name: String): String? =
      System.getenv(name) ?: providers.gradleProperty(name).orNull

    val mavenUrl = getConfig("XROAD_MIRROR_MAVEN_URL")
    val mirrorUsername = getConfig("XROAD_MIRROR_USERNAME")
    val mirrorToken = getConfig("XROAD_MIRROR_TOKEN")

    if (!mavenUrl.isNullOrBlank() && !mirrorUsername.isNullOrBlank() && !mirrorToken.isNullOrBlank()) {
      maven {
        name = "Mirror"
        url = uri(mavenUrl)
        credentials {
          username = mirrorUsername
          password = mirrorToken
        }
      }
    } else {
      mavenCentral()
    }
    mavenLocal()
    maven {
      //TODO Remove once EDC-V and org.eclipse.dataplane-core:dataplane-sdk artifacts are in Maven Central
      url = uri("https://artifactory.niis.org/artifactory/xroad-external-snapshots/")
    }
  }
}

// Common projects
include("arch-rules")

include("common")
include("common:common-admin-api")
include("common:common-api-throttling")
include("common:common-db")
include("common:common-db-identifiers")
include("common:common-domain")
include("common:common-management-request")
include("common:common-management-service")
include("common:common-core")
include("common:common-jetty")
include("common:common-message")

include("common:common-pgp")

// Lib projects
include("lib")
include("lib:asic-core")
include("lib:bootstrap-edc-quarkus")
include("lib:globalconf-impl")
include("lib:globalconf-core")
include("lib:globalconf-spring")
include("lib:serverconf-impl")
include("lib:serverconf-core")
include("lib:serverconf-spring")
include("lib:keyconf-api")
include("lib:keyconf-impl")
include("lib:messagelog-core")
include("lib:properties-core")
include("lib:properties-quarkus")
include("lib:properties-spring")
include("lib:rpc-core")
include("lib:rpc-spring")
include("lib:rpc-quarkus")
include("lib:vault-core")
include("lib:vault-spring")
include("lib:vault-quarkus")
include("lib:health-check-core")

// Service projects
include("service")

include("service:auxiliary-service:auxiliary-service-application")
include("service:auxiliary-service:auxiliary-service-rpc-client")
include("service:auxiliary-service:auxiliary-service-core")

include("service:configuration-client:configuration-client-application")
include("service:configuration-client:configuration-client-common")
include("service:configuration-client:configuration-client-core")
include("service:configuration-client:configuration-client-model")
include("service:configuration-client:configuration-client-rpc-client")

include("service:softtoken-signer:softtoken-signer-application")
include("service:softtoken-signer:softtoken-signer-int-test")

include("service:configuration-proxy:configuration-proxy-application")
include("service:configuration-proxy:configuration-proxy-cli")
include("service:configuration-proxy:configuration-proxy-common")
include("service:configuration-proxy:configuration-proxy-core")
include("service:configuration-proxy:configuration-proxy-jpa")
include("service:configuration-proxy:configuration-proxy-int-test")

include("service:monitor:monitor-application")
include("service:monitor:monitor-api")
include("service:monitor:monitor-core")
include("service:monitor:monitor-rpc-client")

include("service:op-monitor:op-monitor-application")
include("service:op-monitor:op-monitor-api")
include("service:op-monitor:op-monitor-client")
include("service:op-monitor:op-monitor-core")
include("service:op-monitor:op-monitor-db")
include("service:op-monitor:op-monitor-int-test")

include("service:proxy:proxy-application")
include("service:proxy:proxy-core")
include("service:proxy:proxy-rpc-client")
include("service:proxy:proxy-monitoring-api")
include("service:proxy:proxy-dsp-core")

include("service:signer:signer-application")
include("service:signer:signer-api")
include("service:signer:signer-common")
include("service:signer:signer-core")
include("service:signer:signer-jpa")
include("service:signer:signer-cli")
include("service:signer:signer-client")
include("service:signer:signer-client-spring")
include("service:signer:signer-int-test")

include("service:message-log-archiver")
include("service:message-log-archiver:message-log-archiver-cli")
include("service:message-log-archiver:message-log-archiver-core")

include("service:ds-control-plane")
include("service:ds-control-plane:ds-control-plane-application")
include("service:ds-control-plane:ds-control-plane-db")
include("service:ds-control-plane:ds-ext-sample")
include("service:ds-control-plane:ds-xroad-asset-access-api")
include("service:ds-control-plane:ds-xroad-asset-access-protocol")
include("service:ds-control-plane:ds-xroad-control-plane-policy")
include("service:ds-control-plane:ds-xroad-assets")
include("service:ds-control-plane:ds-control-plane-tasks-store-poll-executor")
include("service:ds-data-plane")
include("service:ds-data-plane:ds-data-plane-application")
include("service:ds-data-plane:ds-data-plane-db")
include("service:ds-data-plane:ds-xroad-data-plane")
include("service:ds-data-plane:ds-xroad-data-plane-policy")
include("service:ds-identity-hub")
include("service:ds-identity-hub:ds-identity-hub-application")
include("service:ds-identity-hub:ds-identity-hub-db")
include("service:ds-identity-hub:ds-identity-hub-customization")
include("service:ds-issuer-service")
include("service:ds-issuer-service:ds-issuer-service-application")

// Tool projects
include("tool")
include("tool:asic-verifier-cli")
include("tool:migration-cli")
include("tool:messagelog-archive-verifier")
include("tool:test-framework-core")
include("tool:liquibase-executor")

// Main projects
include("shared-ui")

include("central-server")
include("central-server:openapi-model")
include("central-server:admin-service")
include("central-server:admin-service:core")
include("central-server:admin-service:core-api")
include("central-server:admin-service:infra-api-rest")
include("central-server:admin-service:application")
include("central-server:admin-service:ui")
include("central-server:admin-service:infra-jpa")
include("central-server:admin-service:globalconf-generator")
include("central-server:admin-service:ui-system-test")
include("central-server:admin-service:int-test")
include("central-server:admin-service:api-client")

include("central-server:management-service")
include("central-server:management-service:application")
include("central-server:management-service:core")
include("central-server:management-service:infra-api-soap")
include("central-server:management-service:core-api")
include("central-server:management-service:int-test")

include("central-server:registration-service")

include("security-server")
include("security-server:openapi-model")
include("security-server:admin-service")
include("security-server:admin-service:application")
include("security-server:admin-service:ui")
include("security-server:system-test")
include("security-server:e2e-test")

// Tests
include("common:common-test")
