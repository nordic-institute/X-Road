@SecurityServer
@BackupAndRestore
Feature: 0600 - SS: Backup and Restore

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Settings tab is selected
    And Backup and Restore sub-tab is selected

  Scenario: Configuration can be backed up and deleted
    Given Configuration backup count is equal to 0
    When Configuration backup is created
    Then Configuration backup count is equal to 1
    When Configuration backup is deleted
    Then Configuration backup count is equal to 0

  Scenario: Configuration backup can be downloaded and uploaded
    Given Configuration backup is created
    When Configuration backup is downloaded
    And Configuration backup is deleted
    Then Configuration backup count is equal to 0
    When Configuration backup is uploaded
    Then Configuration backup count is equal to 1

  Scenario: Already existing configuration backup is overwritten on upload
    When Configuration backup count is equal to 1
    And Configuration backup is downloaded
    And Configuration backup is overwritten
    Then Configuration backup count is equal to 1

  Scenario: Configuration can be restored from backup
    Given Clients tab is selected
    And Client "random-sub-1" with status "SAVED" is present in the list
    And Client "random-sub-2" with status "SAVED" is missing in the list
    When Subsystem add page is opened for Client "TestGov"
    And Subsystem code is set to "random-sub-2"
    When Add subsystem form is submitted
    And Register client send registration request dialog is confirmed
    And snackbar is closed
    Then Client "random-sub-2" with status "SAVED" is present in the list
    When Settings tab is selected
    And Backup and Restore sub-tab is selected
    And Configuration backup count is equal to 1
    Then Configuration can be successfully restored from backup
    When Clients tab is selected
    Then Client "random-sub-1" with status "SAVED" is present in the list
    Then Client "random-sub-2" with status "SAVED" is missing in the list

  Scenario: Configuration backups can be filtered
    Given Configuration backup count is equal to 1
    When Configuration backup is created
    Then Configuration backup count is equal to 2
    When Configuration backup filter is set to last created backup
    Then Configuration backup count is equal to 1
