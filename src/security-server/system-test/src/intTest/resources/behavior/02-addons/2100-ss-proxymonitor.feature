@SecurityServer
@Addon
Feature: 2100 - SS: Proxymonitor

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret

  Scenario: Proxymonitor responds with correct response
    Given Clients tab is selected
    And Client "TestGov" is opened
    And Internal servers sub-tab is selected
    Then Internal server connection type is set to "HTTP"
    When Security Server Metrics request was sent with queryId "ID1234"
    Then Valid Security Server Metrics response is returned

  Scenario: Proxymonitor responds with correct response for TotalPhysicalMemory request
    When Security Server Metric: "TotalPhysicalMemory" request was sent with queryId "ID1234"
    Then Valid numeric value returned for metric: "TotalPhysicalMemory"
