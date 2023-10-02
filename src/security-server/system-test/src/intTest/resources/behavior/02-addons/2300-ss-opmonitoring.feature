@SecurityServer
@Addon
Feature: 2300 - SS: Operational monitoring services

  Scenario: Retrieving Operational Data of Security Server
    When Security Server Operational Data request was sent
    Then Valid Security Server Operational data response is returned

  Scenario: Retrieving Health Data of Security Server
    When Security Server Health Data request was sent
    Then Valid Security Server Health Data response is returned
