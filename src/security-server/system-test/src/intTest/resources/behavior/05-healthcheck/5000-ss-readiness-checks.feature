@ReadinessCheck
Feature: 5000 - SS: Readiness Checks
  Verifies that all Security Server services report healthy readiness status
  when the system is properly initialized and running.

  Scenario: Proxy service readiness checks are UP
    Then proxy readiness check is UP
    And "proxy" service readiness check "PROXY_SERVERCONF_DATABASE_READINESS_CHECK" has status "UP"
    And "proxy" service readiness check "PROXY_GLOBALCONF_READINESS_CHECK" has status "UP"
    And "proxy" service readiness check "SIGNER_CHANNEL_READINESS_CHECK" has status "UP"
    And "proxy" service readiness check "CONFCLIENT_CHANNEL_READINESS_CHECK" has status "UP"

  Scenario: Signer service readiness checks are UP
    Then signer readiness check is UP
    And "signer" service readiness check "SIGNER_DATABASE_READINESS_CHECK" has status "UP"
    And "signer" service readiness check "TOKEN_REGISTRY_READINESS_CHECK" has status "UP"

  Scenario: Configuration-client service readiness checks are UP
    Then configuration-client readiness check is UP
    And "configuration-client" service readiness check "GLOBALCONF_READINESS_CHECK" has status "UP"

  Scenario: Op-monitor service readiness checks are UP
    Then op-monitor readiness check is UP
    And "op-monitor" service readiness check "OP_MONITOR_DATABASE_READINESS_CHECK" has status "UP"

  Scenario: Auxiliary-service service readiness checks are UP
    Then auxiliary-service readiness check is UP
