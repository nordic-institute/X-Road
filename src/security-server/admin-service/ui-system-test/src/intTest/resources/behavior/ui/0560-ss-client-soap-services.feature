@SecurityServer
@Client
Feature: 0560 - SS: Client SOAP (through WSDL) services

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected

  Scenario: Client WSDL service is configured
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When WSDL service dialog is opened and url is set to "http://mock-server:1080/test-services/testservice1.wsdl"
    And  WSDL service dialog is opened and url is set to "http://mock-server:1080/test-services/testservice3.wsdl"
    Then Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is present in the list
    And  Service "WSDL (http://mock-server:1080/test-services/testservice3.wsdl)" is present in the list

  Scenario: Client WSDL service is not duplicated
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When  WSDL service dialog is opened and url is set to "http://mock-server:1080/test-services/testservice1.wsdl"
    Then error: "WSDL exists" was displayed

  Scenario: Client WSDL service is not valid
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When  WSDL service dialog is opened and url is set to "https://www.niis.org/"
    Then error: "Invalid WSDL" was displayed

  Scenario: Client WSDL service url does not respond
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When  WSDL service dialog is opened and url is set to "http://mock-server:1080/test-services/missing.wsdl"
    Then error: "WSDL download failed" was displayed

  Scenario: Client WSDL service url is updated
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is updated with url "http://mock-server:1080/test-services/testservice2.wsdl"
    Then Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is missing in the list
    And Service "WSDL (http://mock-server:1080/test-services/testservice2.wsdl)" is present in the list
    When Service "WSDL (http://mock-server:1080/test-services/testservice2.wsdl)" is updated with url "http://mock-server:1080/test-services/testservice1.wsdl"
    Then Service "WSDL (http://mock-server:1080/test-services/testservice2.wsdl)" is missing in the list
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is present in the list

  Scenario: Client WSDL service service testOp1 is edited
    Given Client "TestService" is opened
    And Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is expanded
    And Service with code "testOp1" is opened
    And Service URL is set to "https://www.niis.org/nosuch-updated/", timeout to 45 and tls certificate verification is unchecked. Apply All? Url: checked, timeout: checked, verify Tls: checked
    And Service is saved and success message "Service saved" is shown
    Then Services under "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" are as follows:
      | $serviceCode | $url                                 | $timeout |
      | testOp1      | https://www.niis.org/nosuch-updated/ | 45       |
      | testOpA      | https://www.niis.org/nosuch-updated/ | 45       |
    When Service with code "testOpA" is opened
    And Service URL is set to "https://www.niis.org/second-update/", timeout to 33 and tls certificate verification is checked. Apply All? Url: unchecked, timeout: unchecked, verify Tls: unchecked
    And Service with a warning is saved and success message "Service saved" is shown
    Then Services under "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" are as follows:
      | $serviceCode | $url                                 | $timeout |
      | testOp1      | https://www.niis.org/nosuch-updated/ | 45       |
      | testOpA      | https://www.niis.org/second-update/  | 33       |

  Scenario: Client service has access rights added to it
    When Client "TestService" is opened
    And Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is expanded
    And Service with code "testOp1" is opened
    And Service add subjects dialog is opened
    And Service subject lookup is executed with member name "TestGov" and subsystem code "test"
    And Subject with id "CS:GOV:0245437-2:TestSaved" and "CS:GOV:0245437-2:test-consumer" is selected from the table. There are total 3 entries
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is present
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is present

  Scenario: Client service access rights subjects search filter clearing restore initial state
    When Client "TestService" is opened
    And Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is expanded
    And Service with code "testOp1" is opened
    And Service add subjects dialog is opened
    When Click Search button on subject dialog
    Then The query return 9 entries in the subjects table
    When Adding value for member name, member code, subsystem and then click the remove value button on the input field
    And Click Search button on subject dialog
    Then The query return 9 entries in the subjects table

  Scenario: Client service has one access rights removed
    When Client "TestService" is opened
    And Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is expanded
    And Service with code "testOp1" is opened
    And Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is present
    When Service Access Rights subject with id "CS:GOV:0245437-2:TestSaved" is removed
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is missing
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is present

  Scenario: Client service has all access rights removed
    When Client "TestService" is opened
    And Services sub-tab is selected
    And Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is expanded
    And Service with code "testOp1" is opened
    And Service add subjects dialog is opened
    And Service subject lookup is executed with member name "TestGov" and subsystem code "test"
    And Subject with id "CS:GOV:0245437-2:TestSaved" and "CS:GOV:0245437-2:TestService" is selected from the table. There are total 2 entries
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is present
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is present
    When Service has all Access Rights removed
    Then Service Access Rights table member with id "CS:GOV:0245437-2:TestSaved" is missing
    And Service Access Rights table member with id "CS:GOV:0245437-2:test-consumer" is missing
    And Service Access Rights table member with id "CS:GOV:0245437-2:TestService" is missing

  Scenario: Newly added services are enabled and one of them disabled
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is enabled
    And Service "WSDL (http://mock-server:1080/test-services/testservice3.wsdl)" is enabled
    Then Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is disabled with notice "just disabled."

  Scenario: Newly added service is deleted
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "WSDL (http://mock-server:1080/test-services/testservice3.wsdl)" is deleted
    Then Service "WSDL (http://mock-server:1080/test-services/testservice3.wsdl)" is missing in the list

  Scenario: Service is refreshed
    Given Client "TestService" is opened
    And Services sub-tab is selected
    When Service "WSDL (http://mock-server:1080/test-services/testservice1.wsdl)" is expanded
    Then WSDL Service is refreshed
