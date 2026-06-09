@SecurityServer
@UI
@Client
Feature: 0560 - SS: Client SOAP (through WSDL) services

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Clients tab is selected

  Scenario: Client service access rights subjects search filter clearing restore initial state
    When Client "Test service" is opened
    And Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is expanded
    And Service with code "testOp1" is opened
    And Service add subjects dialog is opened
    When Click Search button on subject dialog
    Then The query return 9 entries in the subjects table
    When Adding value for member name, member code, subsystem and then click the remove value button on the input field
    And Click Search button on subject dialog
    Then The query return 9 entries in the subjects table
