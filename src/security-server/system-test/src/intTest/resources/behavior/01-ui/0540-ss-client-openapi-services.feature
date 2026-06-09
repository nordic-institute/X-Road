@SecurityServer
@UI
@Client
Feature: 0540 - SS: Client OpenApi REST services

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Clients tab is selected

  Scenario: Only manually added endpoints can be edited
    Given Client "Test service" is opened
    And Services sub-tab is selected
    And Service "OPENAPI3 (http://mock-server:1080/test-services/testopenapi2.json)" is expanded
    And Service with code "s4c2" is opened
    When Service endpoints view is opened
    Then Service endpoint with HTTP request method "PUT" and path "/pet" is not editable
    When Service endpoint with HTTP request method "PATCH" and path "/new/path/" has its path changed to "/new/path/edited"
    Then Service endpoint with HTTP request method "PATCH" and path "/new/path/edited" is present in the list
    And Service endpoint with HTTP request method "PATCH" and path "/new/path/" is missing in the list

