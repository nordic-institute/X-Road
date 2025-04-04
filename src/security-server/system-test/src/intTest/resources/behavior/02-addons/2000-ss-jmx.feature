@SecurityServer
@Addon
@Skip
# todo: issues with jmx connection. Port is hardcoded when creating jib container.
# Reconsider the use of JMX. https://nordic-institute.atlassian.net/browse/XRDDEV-2861
Feature: 2000 - SS: JMX monitor

  Scenario: Proxymonitor responds with correct response for TotalPhysicalMemory
    When JMX request for object "TotalPhysicalMemory" attribute "Value"
    Then JMX returned valid numeric value

  Scenario: Proxymonitor responds with correct response for XroadProcessDump
    When JMX request for object "XroadProcessDump" attribute "Value"
    Then JMX returned valid string value
