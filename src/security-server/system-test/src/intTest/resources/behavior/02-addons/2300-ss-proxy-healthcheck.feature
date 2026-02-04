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
    And authentication key "DF9242D3CBDE6DAC8058D2878340C3B527041FD0" named "Auth key" is added to softtoken
    And authentication certificate "5BC622B62052EE89F2020C2FA91872CB49EB1502" is added for key "DF9242D3CBDE6DAC8058D2878340C3B527041FD0"
    And signing key "1342B84B4829BB79226AB268B4D8E70B01068613" named "Sign key" is added to softtoken
    And signing certificate "15A0AFEE2602D2846621118997E268F5FA843C94" is added for member "DEV:COM:1234" under key "1342B84B4829BB79226AB268B4D8E70B01068613"
    And signing key "FA73509F9E9DFB7A3D92B3D34DA6BD20374A24B0" named "TestClient SIGN" is added to softtoken
    And signing certificate "2383ECC7DCE9C81826F99FC79FE96393A342FE42" is added for member "DEV:COM:4321" under key "FA73509F9E9DFB7A3D92B3D34DA6BD20374A24B0"
    And signer service is restarted
    And User logs in token: softToken-0 with PIN: Secret1234
    Then Token: softToken-0 is logged-in
    And healthcheck has no errors

  Scenario: HSM healthcheck has no errors when HSM health check is enabled
    Given healthcheck has no errors
    When HSM health check is enabled on proxy
    And service "proxy" is "restarted"
    Then healthcheck has no errors

  Scenario: Healthcheck is fails when signer is down
    Given healthcheck has no errors
    When service "signer" is "stopped"
    Then healthcheck has errors and error message is "Fetching health check response timed out for: Hardware Security Modules status"
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
