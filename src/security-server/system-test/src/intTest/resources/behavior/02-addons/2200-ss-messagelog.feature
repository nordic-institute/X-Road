@SecurityServer
@Addon
Feature: 2200 - SS: Messagelog addon

  Scenario: Messagelog contains metrics requests
    When Security Server Metrics request was sent with queryId "ID4321"
    Given Messagelog contains 4 entries with queryId "ID4321"
