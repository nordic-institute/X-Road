@CentralServer
@BackupAndRestore
Feature: 1100 - CS: Backup and Restore

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected
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
    Given Configuration backup count is equal to 1
    Then Configuration can be successfully restored from backup
