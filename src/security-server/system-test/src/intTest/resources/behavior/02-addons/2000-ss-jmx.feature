@SecurityServer
@Addon
Feature: 2000 - SS: JMX monitor

  Scenario: Proxymonitor responds with correct response for TotalPhysicalMemory
    When JMX request for object "TotalPhysicalMemory" attribute "Value"
    Then JMX returned valid numeric value

  Scenario: Proxymonitor responds with correct response for TotalPhysicalMemory
    When JMX request for object "XroadProcessDump" attribute "Value"
    Then JMX returned valid string value
