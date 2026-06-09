@SecurityServer
@UI
@Client
Feature: 0500 - SS: Client Add

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Clients tab is selected

  Scenario: Add client was cancelled
    When Add client wizard is opened
    And Add Client details is filled with preselected client "DEV:COM:4321:TestClient" is opened
    And Add Client Token wizard page is closed
    Then Client "TestClient" is missing in the list

