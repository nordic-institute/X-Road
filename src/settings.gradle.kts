// Load local properties if they exist
val localPropertiesFile = file("gradle-local.properties")
if (localPropertiesFile.exists()) {
  gradle.projectsLoaded {
    val localProps = java.util.Properties().apply {
      load(localPropertiesFile.inputStream())
    }
    // Override properties from local file
    localProps.forEach { (key, value) ->
      gradle.rootProject.extensions.extraProperties.set(key.toString(), value.toString())
    }
  }
}

rootProject.name = "x-road-core"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    mavenLocal()
    maven {
      url = uri("https://central.sonatype.com/repository/maven-snapshots/")
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

// Service projects
include("service")

include("service:backup-manager:backup-manager-application")
include("service:backup-manager:backup-manager-rpc-client")
include("service:backup-manager:backup-manager-core")

include("service:configuration-client:configuration-client-application")
include("service:configuration-client:configuration-client-core")
include("service:configuration-client:configuration-client-model")
include("service:configuration-client:configuration-client-rpc-client")

include("service:softtoken-signer:softtoken-signer-application")
include("service:softtoken-signer:softtoken-signer-int-test")

include("service:configuration-proxy:configuration-proxy-application")

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

include("service:signer:signer-application")
include("service:signer:signer-api")
include("service:signer:signer-common")
include("service:signer:signer-core")
include("service:signer:signer-jpa")
include("service:signer:signer-cli")
include("service:signer:signer-client")
include("service:signer:signer-client-spring")
include("service:signer:signer-int-test")

include("service:ds-control-plane")
include("service:ds-control-plane:ds-control-plane-application")
include("service:ds-control-plane:ds-ext-sample")
include("service:ds-data-plane")
include("service:ds-data-plane:ds-data-plane-application")

// Tool projects
include("tool")
include("tool:asic-verifier-cli")
include("tool:migration-cli")
include("tool:messagelog-archive-verifier")
include("tool:test-framework-core")

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
include("security-server:admin-service:infra-jpa")
include("security-server:admin-service:ui")
include("security-server:admin-service:message-log-archiver")
include("security-server:admin-service:message-log-archiver-api")
include("security-server:admin-service:management-rpc-client")
include("security-server:system-test")
include("security-server:e2e-test")

// Tests
include("common:common-test")
