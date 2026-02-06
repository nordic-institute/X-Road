@SecurityServer
@Diagnostics
Feature: 0920 - SS:Diagnostics - Connection Testing

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Diagnostics tab is selected
    And Connection Testing sub-tab is selected

  Scenario: Central Server connection check tests should run
    Given Global configuration download from "http://cs:80/internalconf" status should be failed
    And Global configuration download from "https://cs:443/internalconf" status should be failed
    And Global configuration download Test button should be enabled
    And Central Server authentication certificate registration service status should be failed
    And Central Server authentication certificate registration service Test button should be enabled

  Scenario: Other Security Server connection test can be run
    Given Other Security Server Test button should be disabled
    When Current client is set to DEV:COM:1234
    And Service type is set to REST
    Then Target instance is prefilled with DEV
    When Target client is set to DEV:COM:1234:MANAGEMENT
    Then Target security server is prefilled with SS0
    And Other Security Server Test button should be enabled
    When Run test for Other Security Server
    Then Other Security Server error message should contain server.clientproxy.io_error

  Scenario: Management Security Server test fails
    When Run test for Management Security Server
    Then Management Security Server error message should contain server.serverproxy.service_failed.unknown_member
