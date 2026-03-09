@LivenessCheck
Feature: 6100 - SS: Liveness Checks
  Verifies that all Security Server services report healthy liveness status
  when the system is properly initialized and running.

  Scenario: Proxy service liveness checks are UP
    Then proxy liveness check is UP
    And "proxy" service liveness check "DEADLOCK_CHECK" has status "UP"
    And "proxy" service liveness check "HEAP_MEMORY_CHECK" has status "UP"

  Scenario: Signer service liveness checks are UP
    Then signer liveness check is UP
    And "signer" service liveness check "DEADLOCK_CHECK" has status "UP"
    And "signer" service liveness check "HEAP_MEMORY_CHECK" has status "UP"

  Scenario: Configuration-client service liveness checks are UP
    Then configuration-client liveness check is UP
    And "configuration-client" service liveness check "DEADLOCK_CHECK" has status "UP"
    And "configuration-client" service liveness check "HEAP_MEMORY_CHECK" has status "UP"

  Scenario: Op-monitor service liveness checks are UP
    Then op-monitor liveness check is UP
    And "op-monitor" service liveness check "DEADLOCK_CHECK" has status "UP"
    And "op-monitor" service liveness check "HEAP_MEMORY_CHECK" has status "UP"

  Scenario: Backup-manager service liveness checks are UP
    Then backup-manager liveness check is UP
    And "backup-manager" service liveness check "DEADLOCK_CHECK" has status "UP"
    And "backup-manager" service liveness check "HEAP_MEMORY_CHECK" has status "UP"
