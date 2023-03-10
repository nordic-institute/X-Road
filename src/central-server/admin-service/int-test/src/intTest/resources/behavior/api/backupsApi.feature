@Backups
Feature: Backups API

  Scenario: Backup can be uploaded
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Backup test_backup.tar is uploaded
    And Response is of status code 202
    When Backups are retrieved
    Then Response is of status code 200
    And Backups contains test_backup.tar backup: TRUE

  Scenario: Backup name validated before upload
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Backup incorrect.backup is uploaded
    And Response is of status code 400
    And Backup .incorrect.tar is uploaded
    And Response is of status code 400
    Then Backups are retrieved
    And Backups contains incorrect.backup backup: FALSE
    And Backups contains .incorrect.tar backup: FALSE

  Scenario: Backup listing is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Backups are retrieved
    Then Response is of status code 403
