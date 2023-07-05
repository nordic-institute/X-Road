@CentralServer
@CertificationService
Feature: 0350 - CS: System Settings -> System parameters

  Background:
    Given CentralServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to CentralServer with password secret
    And CentralServer Settings tab is selected

  Scenario: System Parameters is present and can be edited
    Given System settings sub-tab is selected
    And System Parameters card is visible
    And Central Server address is valid.example.org
    When Central Server address edit dialog is opened
    And Central Server address valid-edited.example.org entered in popup
    And Dialog Save button is clicked
    Then Instance Identifier is CS-E2E
    And  Central Server address is valid-edited.example.org
