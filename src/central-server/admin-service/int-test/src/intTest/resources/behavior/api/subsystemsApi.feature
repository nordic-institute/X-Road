@SubsystemsApi
Feature: Subsystems API

  @Modifying
  Scenario: Add new subsystem
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:test-member' is added
    When new subsystem 'CS:E2E:test-member:Subsystem-0' is added
    Then member 'CS:E2E:test-member' subsystems contains 'Subsystem-0'

  @Modifying
  Scenario: Delete subsystem
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:test-member' is added
    And new subsystem 'CS:E2E:test-member:Subsystem-1' is added
    When subsystem 'CS:E2E:test-member:Subsystem-1' is deleted
    Then member 'CS:E2E:test-member' subsystems does not contain 'Subsystem-1'

  @Modifying
  Scenario: Unregister subsystem
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:test-member' is added
    Given new subsystem 'CS:E2E:test-member:Subsystem-1' is added
    And new subsystem 'CS:E2E:test-member:Subsystem-2' is added
    And Authentication header is set to MANAGEMENT_SERVICE
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

  Scenario Outline: Unregister subsystem should fail with invalid IDs
    Given Authentication header is set to REGISTRATION_OFFICER
    Then unregistering subsystem '<$subsystemId>' from security server '<$serverId>' should fail
    Examples:
      | $subsystemId                   | $serverId               |
      | INVALID-FORMAT                 | CS:E2E:test-member:SS-X |
      | TEST:CLASS                     | CS:E2E:test-member:SS-X |
      | TEST:CLASS:CODE                | CS:E2E:test-member:SS-X |
      | CS:E2E:test-member:Subsystem-1 | INVALID-FORMAT          |
      | CS:E2E:test-member:Subsystem-1 | TEST:CLASS              |
      | CS:E2E:test-member:Subsystem-1 | TEST:CLASS:CODE         |
      | INVALID-FORMAT                 | INVALID-FORMAT          |

  Scenario Outline: Deleting subsystem should fail with invalid IDs
    Given Authentication header is set to REGISTRATION_OFFICER
    Then deleting subsystem '<$subsystemId>' should fail
    Examples:
      | $subsystemId    |
      | INVALID-FORMAT  |
      | TEST:CLASS      |
      | TEST:CLASS:CODE |

