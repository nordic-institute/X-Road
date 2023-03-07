@SecurityServer
Feature: Security Server API

  @Modifying
  Scenario: Get list of security servers
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to SYSTEM_ADMINISTRATOR
    Then security servers list contains 'CS:TEST:member-1:SS-X'

  @Modifying
  Scenario: Get security server details
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can get security server 'CS:TEST:member-1:SS-X' details
    And getting non existing security server details fails

  @Modifying
  Scenario: Get security server clients
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-2' is added
    And new security server 'CS:TEST:member-2:SS-2' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    Then security server 'CS:TEST:member-2:SS-2' has no clients
    When new member 'CS:TEST:member-7' is added
    And client 'CS:TEST:member-7' is registered as security server 'CS:TEST:member-2:SS-2' client from 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:TEST:member-2:SS-2' clients contains 'CS:TEST:member-7'

  @Modifying
  Scenario: Modify security server address
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then security server 'CS:TEST:member-1:SS-X' address is updated
    And updating the address of a non-existing security server fails

  @Modifying
  Scenario: Get security server authentication certificates
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can get security server 'CS:TEST:member-1:SS-X' authentication certificates

  @Modifying
  Scenario: Delete security server
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And new member 'CS:TEST:member-2' is added
    And client 'CS:TEST:member-2' is registered as security server 'CS:TEST:member-1:SS-X' client from 'SECURITY_SERVER'
    And management request is approved
    Then security servers list contains 'CS:TEST:member-1:SS-X'
    When user deletes security server 'CS:TEST:member-1:SS-X'
    Then security servers list does not contain 'CS:TEST:member-1:SS-X'

  @Modifying
  Scenario: Deleting security server authentication certificate
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And new member 'CS:TEST:member-1' is added
    And new security server 'CS:TEST:member-1:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to REGISTRATION_OFFICER
    Then user can delete security server 'CS:TEST:member-1:SS-X' authentication certificate
    And security server 'CS:TEST:member-1:SS-X' has no authentication certificates
