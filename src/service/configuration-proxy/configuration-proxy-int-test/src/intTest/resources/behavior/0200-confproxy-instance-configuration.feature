Feature: 0200 - Configuration Proxy: Instance creation and configuration

  Background:
    Given new API key is generated via CLI

  Scenario: Create proxy instance via CLI
    When proxy instance "TEST" is created via CLI
    Then the CLI output contains "Done."
    And proxy instance "TEST" is present in the instance list

  Scenario: View proxy instance configuration via CLI
    When configuration is viewed for proxy instance "TEST"
    Then the CLI output contains "Configuration for proxy 'TEST'"
    And the CLI output contains "Validity interval:"

  Scenario: Add signing key to proxy instance
    When a signing key is generated for proxy instance "TEST" from token "0"
    Then the CLI output contains "Generated key with ID"
    And the CLI output contains "Saved self-signed certificate"
    And proxy instance "TEST" has 1 signing key

  Scenario: Add second signing key and activate it
    When a signing key is generated for proxy instance "TEST" from token "0"
    Then proxy instance "TEST" has 2 signing keys
    When the second signing key of proxy instance "TEST" is activated
    Then the CLI output contains "marked as active signing key"

  Scenario: Delete non-active signing key
    When the first signing key of proxy instance "TEST" is deleted
    Then the CLI output contains "Deleted key from 'conf.ini'."
    And proxy instance "TEST" has 1 signing key

  Scenario: Generate anchor and download configuration
    Given source anchor "/home/xroad/anchors/DEV_anchor.xml" is provisioned for proxy instance "TEST"
    And anchor is generated for proxy instance "TEST" to file "/tmp/anchor_TEST.xml"
    Then the CLI output contains "Generated anchor xml to '/tmp/anchor_TEST.xml'"
    When configuration is downloaded using anchor "/tmp/anchor_TEST.xml"
    Then the CLI output contains "Successfully downloaded configuration to:"
