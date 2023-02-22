@SecurityServer
Feature: Security Server API

  Scenario: Security server get certs fails
    When Security server auth certs for "123" is requested
    Then Response is of status code 403

  @Modifying
  Scenario: Get list of security servers
    Given member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered
    And management request is approved
    Then security servers list contains 'CS:TEST:member-1:SS-X'

  @Modifying
  Scenario: Get security server details
    Given member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered
    And management request is approved
    Then user can get security server 'CS:TEST:member-1:SS-X' details
    And getting non existing security server details fails

  @Modifying
  Scenario: Get security server clients
    Given member class 'TEST' is created
    And new member 'CS:TEST:member-2' is added
    And new security server 'CS:TEST:member-2:SS-2' authentication certificate registered
    And management request is approved
    Then security server 'CS:TEST:member-2:SS-2' has no clients
    When new member 'CS:TEST:member-7' is added
    And new client 'CS:TEST:member-7' is registered for security server 'CS:TEST:member-2:SS-2'
    And management request is approved
    Then security server 'CS:TEST:member-2:SS-2' clients contains 'CS:TEST:member-7'

  @Modifying
  Scenario: Modify security server address
    Given member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered
    And management request is approved
    Then security server 'CS:TEST:member-1:SS-X' address is updated
    And Updating the address of a non-existing security server fails

