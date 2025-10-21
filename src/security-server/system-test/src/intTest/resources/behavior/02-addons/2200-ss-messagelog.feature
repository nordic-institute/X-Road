@SecurityServer
@Addon
Feature: 2200 - SS: Messagelog addon

  @Skip #TODO beta1 release preparation
  Scenario: Messagelog contains metrics requests
    When Security Server Metrics request was sent with queryId "ID4321"
    Given Messagelog contains 4 entries with queryId "ID4321"

  Scenario: Get verification configuration for the asicverifier tool
    Given verification configuration can be downloaded
