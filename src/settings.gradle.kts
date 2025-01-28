rootProject.name = "x-road-core"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// Common projects
include("arch-rules")

include("common")
include("common:common-acme")
include("common:common-admin-api")
include("common:common-api-throttling")
include("common:common-db")
include("common:common-domain")
include("common:common-mail")
include("common:common-management-request")
include("common:common-messagelog")
include("common:common-rpc")
include("common:common-int-test")
include("common:common-core")
include("common:common-jetty")
include("common:common-message")
include("common:common-properties")
include("common:common-scheduler")

// Lib projects
include("lib")
include("lib:asic-core")
include("lib:bootstrap-quarkus")
include("lib:globalconf-impl")
include("lib:globalconf-core")
include("lib:globalconf-spring")
include("lib:serverconf-impl")
include("lib:serverconf-core")
include("lib:serverconf-spring")
include("lib:keyconf-api")
include("lib:keyconf-impl")

// Service projects
include("service")

include("service:configuration-client:configuration-client-application")
include("service:configuration-client:configuration-client-core")

include("service:configuration-proxy:configuration-proxy-application")

include("service:monitor:monitor-application")
include("service:monitor:monitor-api")
include("service:monitor:monitor-core")

include("service:op-monitor:op-monitor-application")
include("service:op-monitor:op-monitor-api")
include("service:op-monitor:op-monitor-core")

include("service:proxy:proxy-application")
include("service:proxy:proxy-core")

include("service:signer:signer-application")
include("service:signer:signer-api")
include("service:signer:signer-core")
include("service:signer:signer-cli")
include("service:signer:signer-client")

include("service:message-log-archiver:message-log-archiver-application")

// Tool projects
include("tool")
include("tool:asic-verifier-cli")

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
include("security-server:admin-service:int-test")
include("security-server:system-test")
include("security-server:e2e-test")

// Tests
include("common:common-test")

// Addons
include("addons:hwtoken")
include("addons:messagelog:messagelog-addon")
include("addons:messagelog:messagelog-archive-verifier")
include("addons:messagelog:messagelog-db")
include("addons:metaservice")

include("addons:proxymonitor-common")
project(":addons:proxymonitor-common").projectDir = file("addons/proxymonitor/common")

include("addons:proxymonitor-metaservice")
project(":addons:proxymonitor-metaservice").projectDir = file("addons/proxymonitor/metaservice")

include("addons:op-monitoring")
include("addons:wsdlvalidator")
