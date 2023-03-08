@SubsystemsApi
@Modifying
Feature: Subsystems API

  Background:
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'E2E' is created
    And new member 'CS:E2E:test-member' is added

  Scenario: Add new subsystem
    When new subsystem 'CS:E2E:test-member:Subsystem-0' is added
    Then member 'CS:E2E:test-member' subsystems contains 'Subsystem-0'

  Scenario: Delete subsystem
    And new subsystem 'CS:E2E:test-member:Subsystem-1' is added
    When subsystem 'CS:E2E:test-member:Subsystem-1' is deleted
    Then member 'CS:E2E:test-member' subsystems does not contain 'Subsystem-1'

  Scenario: Unregister subsystem
    Given new subsystem 'CS:E2E:test-member:Subsystem-1' is added
    And new subsystem 'CS:E2E:test-member:Subsystem-2' is added
    And new security server 'CS:E2E:test-member:SS-X' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And client 'CS:E2E:test-member:Subsystem-1' is registered as security server 'CS:E2E:test-member:SS-X' client from 'SECURITY_SERVER'
    And management request is approved
    And client 'CS:E2E:test-member:Subsystem-2' is registered as security server 'CS:E2E:test-member:SS-X' client from 'SECURITY_SERVER'
    And management request is approved
    And security server 'CS:E2E:test-member:SS-X' clients contains 'CS:E2E:test-member:Subsystem-1'
    And security server 'CS:E2E:test-member:SS-X' clients contains 'CS:E2E:test-member:Subsystem-2'
    When subsystem 'CS:E2E:test-member:Subsystem-1' is unregistered from 'CS:E2E:test-member:SS-X'
    Then security server 'CS:E2E:test-member:SS-X' clients do not contain 'CS:E2E:test-member:Subsystem-1'
    And security server 'CS:E2E:test-member:SS-X' clients contains 'CS:E2E:test-member:Subsystem-2'
