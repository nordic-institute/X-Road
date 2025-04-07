@SecurityServer
Feature: 0200 - SS: After installation check

  Scenario: Verify that documentation files are installed
    Given file "/usr/share/doc/xroad-addon-metaservices/LICENSE.txt" exists
    And file "/usr/share/doc/xroad-proxy/LICENSE.txt" exists
