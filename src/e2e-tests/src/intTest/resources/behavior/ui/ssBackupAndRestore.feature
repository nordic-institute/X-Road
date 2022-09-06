@SecurityServer
@Backup
@Restore
Feature: Security Server Security Officer permissions

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret

  Scenario: Backups is created, listed, filtered and removed
    When SecurityServer Settings tab is selected
    And Backup and restore tab is selected
    When A new backup is created
    Then A newly created backup is filtered and visible
    And A newly created backup is deleted

  Scenario: Backup is downloaded and then imported
    When SecurityServer Settings tab is selected
    And Backup and restore tab is selected
    When A new backup is created
    And A newly created backup is downloaded
    Then Downloaded backup is uploaded
