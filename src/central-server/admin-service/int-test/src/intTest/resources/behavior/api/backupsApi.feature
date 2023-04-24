@Backups
Feature: Backups API

  Background:
    Given Authentication header is set to SYSTEM_ADMINISTRATOR

  @Modifying
  @ClearBackups
  Scenario: Backup can be created
    When Backup is created
    Then Response is of status code 201
    When Backups are retrieved
    Then Response is of status code 200
    And it contains data of new backup file

  @Modifying
  @ClearBackups
  Scenario: Backup can be uploaded
    And Backup test_backup.gpg is uploaded
    And Response is of status code 201
    When Backups are retrieved
    Then Response is of status code 200
    And Backups contains test_backup.gpg backup: TRUE

  Scenario: Backup name validated before upload
    And Backup incorrect.backup is uploaded
    And Response is of status code 400 and error code "invalid_filename"
    And Backup .incorrect.gpg is uploaded
    And Response is of status code 400 and error code "invalid_filename"
    Then Backups are retrieved
    And Response is of status code 200
    And Backups contains incorrect.backup backup: FALSE
    And Backups contains .incorrect.gpg backup: FALSE

  @Modifying
  @ClearBackups
  Scenario: Backup can be downloaded
    And Backup test_backup.gpg is uploaded
    And Response is of status code 201
    When Backup named test_backup.gpg is downloaded
    Then Response is of status code 200

  @Modifying
  @ClearBackups
  Scenario: Backup can be deleted
    Given Backup test_backup.gpg is uploaded
    And Response is of status code 201
    When Backup test_backup.gpg is deleted
    Then Response is of status code 204
    And Backups are retrieved
    And Response is of status code 200
    And Backups contains test_backup.gpg backup: FALSE

  Scenario: Backup can't be found for download
    When Backup named doesnt-exist-test-backup.gpg is downloaded
    Then Response is of status code 404 and error code "backup_file_not_found"

  Scenario: Backup upload is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Backup test_backup.gpg is uploaded
    Then Response is of status code 403

  Scenario: Backup download is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Backup named test_backup.gpg is downloaded
    Then Response is of status code 403

  Scenario: Backup listing is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Backups are retrieved
    Then Response is of status code 403

  Scenario: Backup creation is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Backup is created
    Then Response is of status code 403

  Scenario: Backup deletion is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Backup test_backup.gpg is deleted
    Then Response is of status code 403

  @ClearBackups
  Scenario: Restore central server configuration from a backup
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Backup test_backup.gpg is uploaded
    And Signer.getTokens response is mocked
    Then Central server is restored from test_backup.gpg
