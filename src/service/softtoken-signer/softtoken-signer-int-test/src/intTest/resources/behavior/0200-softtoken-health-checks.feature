@SoftToken
@HealthCheck
Feature: 0200 - Softtoken-Signer: Health Check Probes

  Background:
    Given signer is initialized and keys are synchronized

  Scenario: Liveness probe reports UP after successful sync
    When softtoken-signer liveness endpoint is queried
    Then the health response status is "UP"
    And the health response contains check "SOFTTOKEN_SYNC_LIVENESS" with status "UP"

  Scenario: Readiness probe reports UP after successful sync
    When softtoken-signer readiness endpoint is queried
    Then the health response status is "UP"
    And the health response contains check "SOFTTOKEN_SYNC_READINESS" with status "UP"

  Scenario: Combined health endpoint reports UP
    When softtoken-signer health endpoint is queried
    Then the health response status is "UP"
    And the health response contains check "SOFTTOKEN_SYNC_LIVENESS" with status "UP"
    And the health response contains check "SOFTTOKEN_SYNC_READINESS" with status "UP"

  Scenario: Liveness probe includes threshold data
    When softtoken-signer liveness endpoint is queried
    Then the health response contains check "SOFTTOKEN_SYNC_LIVENESS" with data "threshold" equal to 3
    And the health response contains check "SOFTTOKEN_SYNC_LIVENESS" with data "consecutive_failures" equal to 0

  Scenario: Readiness probe includes sync timing data
    When softtoken-signer readiness endpoint is queried
    Then the health response contains check "SOFTTOKEN_SYNC_READINESS" with data key "last_successful_sync"
    And the health response contains check "SOFTTOKEN_SYNC_READINESS" with data key "threshold_seconds"

  Scenario: Liveness probe reports DOWN after signer service is stopped
    When signer service is stopped
    And health check failure threshold is reached
    And softtoken-signer liveness endpoint is queried
    Then the health response HTTP status code is 503
    And the health response status is "DOWN"
    And the health response contains check "SOFTTOKEN_SYNC_LIVENESS" with status "DOWN"
    And signer service is started

  Scenario: Readiness probe reports DOWN after sync age exceeds threshold
    When signer service is stopped
    And sync age threshold is exceeded
    And softtoken-signer readiness endpoint is queried
    Then the health response HTTP status code is 503
    And the health response status is "DOWN"
    And the health response contains check "SOFTTOKEN_SYNC_READINESS" with status "DOWN"
    And the health response contains check "SOFTTOKEN_SYNC_READINESS" with data key "elapsed_seconds"
    And signer service is started

  Scenario: Health probes recover after signer service is restarted
    When signer service is stopped
    And health check failure threshold is reached
    And signer service is started
    And keys are synchronized after recovery
    And softtoken-signer health endpoint is queried
    Then the health response HTTP status code is 200
    And the health response status is "UP"
    And the health response contains check "SOFTTOKEN_SYNC_LIVENESS" with status "UP"
    And the health response contains check "SOFTTOKEN_SYNC_READINESS" with status "UP"
