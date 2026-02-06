@SecurityServer
Feature: 0200 - SS: After installation check

  Scenario: Verify that documentation files are installed
    Given file "/usr/share/doc/xroad-addon-messagelog/LICENSE.txt" exists
    And file "/usr/share/doc/xroad-proxy/LICENSE.txt" exists

  Scenario: Verify that log files exists
    Given file "/var/log/xroad/clientproxy_access.log" exists
    And file "/var/log/xroad/configuration_client.log" exists
    And file "/var/log/xroad/messagelog-archiver.log" exists
    And file "/var/log/xroad/op-monitor.log" exists
    And file "/var/log/xroad/proxy.log" exists
    And file "/var/log/xroad/proxy_ui_api.log" exists
    And file "/var/log/xroad/proxy_ui_api_access.log" exists
    And file "/var/log/xroad/serverproxy_access.log" exists
    And file "/var/log/xroad/signer.log" exists
