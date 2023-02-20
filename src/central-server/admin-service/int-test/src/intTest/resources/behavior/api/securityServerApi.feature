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
