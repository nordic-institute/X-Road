@SubsystemsApi
@Modifying
Feature: Subsystems API

  Background:
    Given Authentication header is set to MANAGEMENT_SERVICE

  Scenario: Add new subsystem
    And member class 'E2E' is created
    And new member 'CS:E2E:test-member' is added
    When new subsystem 'CS:E2E:test-member:Subsystem-0' is added
    Then member 'CS:E2E:test-member' subsystems contains 'Subsystem-0'

  Scenario: Delete subsystem
    And member class 'E2E' is created
    And new member 'CS:E2E:test-member' is added
    And new subsystem 'CS:E2E:test-member:Subsystem-1' is added
    When subsystem 'CS:E2E:test-member:Subsystem-1' is deleted
    Then member 'CS:E2E:test-member' subsystems does not contain 'Subsystem-1'
