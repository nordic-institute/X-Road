@SecurityServer
@UI
@Skip #TODO either remove altogether or bring back those files to containers
Feature: 0200 - SS: After installation check

  Scenario: Verify that documentation files are installed
    And file "/usr/share/doc/xroad-proxy/LICENSE.txt" exists
