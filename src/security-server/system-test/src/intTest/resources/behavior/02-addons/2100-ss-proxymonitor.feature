@SecurityServer
@Addon
Feature: 2100 - SS: Proxymonitor

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected
    When Client "TestGov" is opened
    And Internal servers sub-tab is selected
    Then Internal server connection type is set to "HTTP"

  Scenario: Proxymonitor responds with correct response
    When Security Server Metrics request was sent with queryId "ID1234"
    Then Valid Security Server Metrics response is returned
