Feature: 0100 - Op monitoring data in intervals

  Background:
    Given op-monitor client is initialized

  Scenario: Query with no optional filters
    When user asks for traffic data of last 1 hour in 30 minute intervals
    Then the query returns 4 successful requests and 1 failed requests

  Scenario: Query with security server type
    When user asks for traffic data of last hour in 45 minute intervals where security server was "Client"
    Then the query returns 2 successful requests and 1 failed requests

  Scenario: Query with member
    When user asks for traffic data of last hour in 60 minute intervals where one of the participants was "DEV:COM:1234:System1"
    Then the query returns 3 successful requests and 1 failed requests

  Scenario: Query with service
    When user asks for traffic data of last hour in 30 minute intervals where requested service was "DEV:COM:1234:Service9:getTopSecret.v2"
    Then the query returns 1 successful requests and 0 failed requests

  Scenario: Query with security server type and member
    When user asks for traffic data of last two hour in 30 minute intervals where "Client" was "DEV:COM:1234:System1"
    Then the query returns 2 successful requests and 1 failed requests

  Scenario: Query with member and service
    When user asks for traffic data of last two hour in 30 minute intervals where one of the participants was "DEV:COM:1234:System1" and requested service was "DEV:COM:4321:Service1:xroadGetRandom.v1"
    Then the query returns 2 successful requests and 1 failed requests

  Scenario: Results in different time buckets
    When user asks for traffic data of last 2 hour in 30 minute intervals
    Then the query returns intervals with correct success and failure counts
