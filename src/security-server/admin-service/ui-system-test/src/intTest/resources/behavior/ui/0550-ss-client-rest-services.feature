@SecurityServer
@Client
Feature: 0550 - SS: Client REST with base path services

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Client service with Base Path is configured
    Given Client "TestService" is opened
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

  Scenario: Client Rest service with duplicate service code is not added
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Rest service dialog is opened and base path is set to "http://example2.com" and service code "s3c1"
    Then Dialog data is saved and error message "Service code already exists" is shown

  Scenario: Client Rest service with duplicate url is not added
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Rest service dialog is opened and base path is set to "http://example.com" and service code "s3c10"
    Then Dialog data is saved and error message "URL already exists" is shown

  Scenario: Updating service url to duplicate url is not allowed
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "REST (http://example2.com)" is updated with url "http://example.com" and service code "s3c1x"
    Then Rest service details are saved and error message "URL already exists" is shown

  Scenario: Client service is edited
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "REST (http://example.com)" is expanded
    And Service with code "s3c1" is opened
    When Service URL is set to "http://example.com/v2", timeout to 30 and tls certificate verification is unchecked
    Then Service is saved and success message "Service saved" is shown
    When Service with code "s3c1" is opened
    Then Service URL is "http://example.com/v2", timeout is 30 and tls certificate verification is unchecked
    When Service URL is set to "http://example2.com", timeout to 30 and tls certificate verification is unchecked
    Then Rest service parameters are saved and error message "URL already exists" is shown

  Scenario: Client service has access rights added to it
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "REST (http://example.com/v2)" is expanded
    And Service with code "s3c1" is opened
    And Service add subjects dialog is opened
    And Service subject lookup is executed with member name "TestGov" and subsystem code "test"
    When Subject with id "CS:GOV:0245437-2:TestSaved" and "CS:GOV:0245437-2:test-consumer" is selected from the table. There are total 3 entries
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is present
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is present

  Scenario: Client service has one access rights removed
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "REST (http://example.com/v2)" is expanded
    And Service with code "s3c1" is opened
    And Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is present
    When Service Access Rights subject with id "CS:GOV:0245437-2:TestSaved" is removed
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is missing
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is present

  Scenario: Client service has all access rights removed
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "REST (http://example.com/v2)" is expanded
    And Service with code "s3c1" is opened
    And Service add subjects dialog is opened
    And Service subject lookup is executed with member name "TestGov" and subsystem code "test"
    When Subject with id "CS:GOV:0245437-2:TestSaved" and "CS:GOV:0245437-2:TestService" is selected from the table. There are total 2 entries
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is present
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is present
    When Service has all Access Rights removed
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is missing
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is missing
    And Service Access Rights table member with id "CS:GOV:0245437-2:TestService" is missing

  Scenario: Client service has new endpoint added to it
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "REST (http://example.com/v2)" is expanded
    And Service with code "s3c1" is opened
    And Service endpoints view is opened
    When Service endpoint with HTTP request method "PATCH" and path "/new/path/" is added
    Then Service endpoint with HTTP request method "PATCH" and path "/new/path/" is present in the list
    When Service endpoint with duplicated HTTP request method "PATCH" and path "/new/path/" is not added
    Then Service endpoint with HTTP request method "PATCH" and path "/new/path/" is present in the list

  Scenario: Manually added endpoints can be edited
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "REST (http://example.com/v2)" is expanded
    And Service with code "s3c1" is opened
    When Service endpoints view is opened
    When Service endpoint with HTTP request method "PATCH" and path "/new/path/" has its path changed to "/new/path/edited"
    Then Service endpoint with HTTP request method "PATCH" and path "/new/path/edited" is present in the list
    And Service endpoint with HTTP request method "PATCH" and path "/new/path/" is missing in the list

  Scenario: Manually added endpoints can be deleted
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "REST (http://example.com/v2)" is expanded
    And Service with code "s3c1" is opened
    When Service endpoints view is opened
    When Service endpoint with HTTP request method "PATCH" and path "/new/path/edited" is deleted
    And Service endpoint with HTTP request method "PATCH" and path "/new/path/edited" is missing in the list

  Scenario: Newly added services are enabled and one of them disabled
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "REST (http://example2.com)" is enabled
    And Service "REST (http://example.com/v2)" is enabled
    Then Service "REST (http://example.com/v2)" is disabled with notice "just disabled."

  Scenario: Newly added service is edited
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "REST (http://example.com/v2)" is updated with url "http://example.com/v3" and service code "s5c200"
    Then Rest service details are saved and success message "Description saved" is shown
    And Service "REST (http://example.com/v2)" is missing in the list
    And Service "REST (http://example.com/v3)" is present in the list

  Scenario: Newly added service is deleted
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "REST (http://example.com/v3)" is deleted
    Then Service "REST (http://example.com/v3)" is missing in the list
