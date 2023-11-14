@HealthCheck
Feature: 2400 - SS Proxy: healthcheck

  Scenario: Valid and registered AUTH key is forcibly enable
  Goal of this scenario is to force "valid" AUTH key that is already registered against
  globalconf. With this key is considered ready to be used by SS.

    Given healthcheck has errors and error message is "No certificate chain available in authentication key."
    When predefined signer softtoken is uploaded
    And healthcheck has no errors

  Scenario: Healthcheck is fails HSM is not operational
    Given healthcheck has no errors
    When property "hsm-health-check-enabled" is set to "true"
    And service "xroad-proxy" is "restarted"
    Then healthcheck has errors and error message is "At least one HSM are non operational"
    When property "hsm-health-check-enabled" is set to "false"
    And service "xroad-proxy" is "restarted"
    Then healthcheck has no errors

  Scenario: Healthcheck is fails when signer is down
    Given healthcheck has no errors
    When service "xroad-signer" is "stopped"
    Then healthcheck has errors and error message is "Fetching health check response timed out for: Authentication key OCSP status"
    When service "xroad-signer" is "started"
    Then healthcheck has no errors

  Scenario: Healthcheck is fails when database is down
    Given healthcheck has no errors
    When service "postgres" is "stopped"
    Then healthcheck has errors and error message is "ServerConf is not available"
    When service "postgres" is "started"
    Then healthcheck has no errors
