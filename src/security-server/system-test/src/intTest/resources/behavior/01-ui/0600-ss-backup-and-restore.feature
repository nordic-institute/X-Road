@SecurityServer
@UI
@BackupAndRestore
Feature: 0600 - SS: Backup and Restore

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Settings tab is selected
    And Backup and Restore sub-tab is selected

  Scenario: Configuration backups can be filtered
    When Configuration backup is created
    When Configuration backup filter is set to last created backup
    Then Configuration backup count is equal to 1
