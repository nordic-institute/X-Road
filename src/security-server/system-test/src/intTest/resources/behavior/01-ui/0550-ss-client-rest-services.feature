@SecurityServer
@UI
@Client
Feature: 0550 - SS: Client REST with base path services

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Clients tab is selected

  Scenario: Client service with Base Path is configured
    Given Client "Test service" is opened
    And Services sub-tab is selected
    When Rest service dialog is opened and base path is set to "invalid-url" and service code "asd"
    Then Form shows an error "URL is not valid"

    When Dialog is closed
    And Rest service dialog is opened and base path is set to "http://example.com" and service code " "
    Then Form shows an error "The Service Code field is required"

    When Dialog is closed
    And Rest service dialog is opened and base path is set to "http://example.com" and service code "s3c1"
    Then Dialog data is saved and success message "REST service added" is shown
    When Rest service dialog is opened and base path is set to "http://example2.com" and service code "s3c2"
    Then Dialog data is saved and success message "REST service added" is shown
    And  Service "REST (http://example.com)" is present in the list
    And  Service "REST (http://example2.com)" is present in the list

