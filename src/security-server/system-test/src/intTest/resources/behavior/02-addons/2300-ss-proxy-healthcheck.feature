@HealthCheck
Feature: 2300 - SS Proxy: healthcheck

  Scenario: Valid and registered AUTH key is forcibly enabled
  Goal of this scenario is to force "valid" AUTH key that is already registered against
  globalconf. With this key is considered ready to be used by SS.

    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Keys and certificates tab is selected
    When Token: softToken-0 edit page is opened
    And Change the pin section is expanded
    And PIN is changed from "T0ken1zer3" to "Secret1234"
    Then Token: softToken-0 is logged-out
    And healthcheck has errors and error message is "No certificate chain available in authentication key."
    When HSM tokens are deleted
    And All Signer keys are deleted
    And authentication key "E67CCA8E9B3DA52DB740CDCDC0926F356F431063" named "Auth key" is added to softtoken
    And authentication certificate "D7D15F0ED1A1320EBA0190C838506B60EC07C994" is added for key "E67CCA8E9B3DA52DB740CDCDC0926F356F431063"
    And signing key "056A952E76B40A46C07628C7B13E5934E39A9C78" named "Sign key" is added to softtoken
    And signing certificate "E3DC911F8E2EB7AD3BE2D65748F6B7048936EDFE" is added for member "DEV:COM:1234" under key "056A952E76B40A46C07628C7B13E5934E39A9C78"
    And signing key "A1B0BEB1E088E3A291AEEC57FB04400BF17D3E0D" named "TestClient SIGN" is added to softtoken
    And signing certificate "84E4773AFCC4051226ACAEF9AC256AAE4059EE93" is added for member "DEV:COM:4321" under key "A1B0BEB1E088E3A291AEEC57FB04400BF17D3E0D"
    And signer service is restarted
    And User logs in token: softToken-0 with PIN: Secret1234
    Then Token: softToken-0 is logged-in
    And healthcheck has no errors

  Scenario: Healthcheck is fails when HSM is not operational
    Given healthcheck has no errors
    When HSM health check is enabled on proxy
    And service "proxy" is "restarted"
    Then healthcheck has errors and error message is "At least one HSM are non operational"
    When HSM health check is disabled on proxy
    And service "proxy" is "restarted"
    Then healthcheck has no errors

  Scenario: Healthcheck is fails when signer is down
    Given healthcheck has no errors
    When service "signer" is "stopped"
    Then healthcheck has errors and error message is "Fetching health check response timed out for: Authentication key OCSP status"
    When service "signer" is "started"
    Then healthcheck has no errors

  #This fails with a different message. Should be re-enabled if we change health-check implementation
  @Skip
  Scenario: Healthcheck is fails when database is down
    Given healthcheck has no errors
    When service "postgres" is "stopped"
    Then healthcheck has errors and error message is "ServerConf is not available"
    When service "postgres" is "started"
    Then healthcheck has no errors
