@MemberClass
@Modifying
Feature: Member Class Api

  Background:
    Given Authentication header is set to SECURITY_OFFICER

 Scenario: Create duplicate member class is not allowed
   Given member class 'TEST' is created
   When member class 'TEST' is created
   Then Response is of status code 409 and error code 'member_class_exists'

  Scenario: Delete member class
    Given member class 'TEST' is created
    And member class list contains 1 items
    When member class 'TEST' is deleted
    Then member class list contains 0 items

  Scenario: Delete member class not allowed when members exist
    Given member class 'TEST' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:TEST:member' is added
    And Authentication header is set to SECURITY_OFFICER
    When member class 'TEST' is deleted
    Then Response is of status code 409 and error code 'member_class_is_in_use'
    And member class list contains 1 items

  Scenario: List member classes
    Given member class 'TEST-1' is created
    And member class 'TEST-2' is created
    And member class 'TEST-3' is created
    Then member class list contains 3 items

  Scenario: Update member class description
    Given member class 'TEST' is created with description 'Class description'
    When member class 'TEST' description is updated to 'New description'
    Then member class 'TEST' has description 'New description'
