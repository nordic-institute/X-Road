@SecurityServer
@Addon
Feature: 0110 - SS: JMX monitor

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested

  Scenario: Proxymonitor responds with correct response for TotalPhysicalMemory
    When JMX request for object "TotalPhysicalMemory" attribute "Value"
    Then JMX returned valid numeric value

  Scenario: Proxymonitor responds with correct response for TotalPhysicalMemory
    When JMX request for object "XroadProcessDump" attribute "Value"
    Then JMX returned valid string value
