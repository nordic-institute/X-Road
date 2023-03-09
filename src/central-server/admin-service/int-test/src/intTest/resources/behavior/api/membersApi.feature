@Members
Feature: Members Api

  @Modifying
  Scenario: Get member details
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    When new member 'CS:TEST:member' is added
    Then user can retrieve member 'CS:TEST:member' details

  @Modifying
  Scenario: Delete member
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member' is added
    When user deletes member 'CS:TEST:member'
    When user requests member 'CS:TEST:member' details
    Then Response is of status code 404 and error code 'member_not_found'

  @Modifying
  Scenario: Update member name
    Given Authentication header is set to MANAGEMENT_SERVICE
    And member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member' is added with name 'memberName'
    When user updates member 'CS:TEST:member' name to 'anotherName'
    Then member 'CS:TEST:member' name is 'anotherName'

  Scenario: Owned servers and global groups for not existing members
    Given Authentication header is set to REGISTRATION_OFFICER
    Then Owned servers list for not existing member should be empty
    Then Global groups for not existing member should be empty
