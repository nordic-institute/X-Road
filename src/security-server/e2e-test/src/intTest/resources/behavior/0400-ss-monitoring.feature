@Initialization
Feature: 0400 - SS: Monitoring

  Background:
    Given Environment is initialized

  Scenario: Call REST and OPENAPI3 methods
    When REST request is sent to "ss1" "proxy"
    """json
    {"data": 1.0, "service": "random"}
    """
    Then response is received with http status code 200 and body path "message" is equal to "Hello, world from POST service!"
    When REST request targeted at "/api/members" API endpoint is sent to "ss1" "proxy"
    Then response is received with http status code 200 and body path "[0].name" is equal to "MTÜ Nordic Institute for Interoperability Solutions"
    And REST request targeted at unsaved "/notexist/test" API endpoint is attempted on "ss1" "proxy"

  Scenario: Proxymonitor responds with correct response
    Given "ss1" owner client internal connection type is set to "HTTP"
    When proxymonitor getSecurityServerMetrics request is sent to "ss1" with queryId "PMID-E2E-1"
    Then proxymonitor response contains metricSet name "SERVER:DEV/COM/4321/SS1"

  Scenario: Proxymonitor responds with correct response for TotalPhysicalMemory request
    Given "ss1" owner client internal connection type is set to "HTTP"
    When proxymonitor getSecurityServerMetrics request for metric "TotalPhysicalMemory" is sent to "ss1" with queryId "PMID-E2E-2"
    Then proxymonitor response contains a numeric value for metric "TotalPhysicalMemory"

  @Skip
  Scenario: Messagelog contains metrics requests
    Given "ss1" owner client internal connection type is set to "HTTP"
    When proxymonitor getSecurityServerMetrics request is sent to "ss1" with queryId "MSGLOG-E2E-UNIQUE-9f3a"
    Then "ss1" messagelog contains 4 encrypted entries for queryId "MSGLOG-E2E-UNIQUE-9f3a"

  Scenario: Retrieving Operational Data of Security Server
    Given "ss0" owner client internal connection type is set to "HTTP"
    When REST request is sent to "ss1" "proxy"
    """json
    {"data": 1.0, "service": "random"}
    """
    Then response is received with http status code 200 and body path "message" is equal to "Hello, world from POST service!"
    When REST request targeted at "/api/members" API endpoint is sent to "ss1" "proxy"
    Then response is received with http status code 200 and body path "[0].name" is equal to "MTÜ Nordic Institute for Interoperability Solutions"
    And REST request targeted at unsaved "/notexist/test" API endpoint is attempted on "ss1" "proxy"
    When getSecurityServerOperationalData request is sent to "ss0"
    Then operational data response contains records with serviceSecurityServerAddress "xrd-ss0"

  Scenario: Retrieving Health Data of Security Server
    Given "ss0" owner client internal connection type is set to "HTTP"
    When REST request is sent to "ss1" "proxy"
    """json
    {"data": 1.0, "service": "random"}
    """
    Then response is received with http status code 200 and body path "message" is equal to "Hello, world from POST service!"
    When getSecurityServerHealthData request is sent to "ss0"
    Then health data response has statisticsPeriodSeconds 600 and at least 1 successfulRequestCount
