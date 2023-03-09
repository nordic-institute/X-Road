@Backups
Feature: Backups API

  Scenario: Available backups are listed
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    When Backups are retrieved
    Then Response is of status code 200
    #TODO once add it created,extend this test-case with validation


  Scenario: Backup listing is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Backups are retrieved
    Then Response is of status code 403
