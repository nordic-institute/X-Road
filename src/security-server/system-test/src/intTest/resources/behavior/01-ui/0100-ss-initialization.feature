@SecurityServer
@UI
@Initialization
Feature: 0100 - SS: Initialization
  Verify that SS can be initialized from fresh state.

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!

  Scenario: Security server is initialized
    Given Proxy healthcheck check "PROXY_GLOBALCONF_READINESS_CHECK" is "UP" with status "UNINITIALIZED"
    And Initial Configuration form is visible
    And Configuration anchor "configuration_anchor_CS_internal.xml" is uploaded
    And Configuration anchor details are confirmed
    Then Configuration anchor selection is submitted
    When Initial configuration of Owner member is set to class: COM, code: 1234 & Security Server Code: SS0
    Then Owner member configuration is submitted
    And Alert about token policy being enforced is present
    When PIN is set to "T0ken1zer3"
    And Confirmation PIN is set to "T0ken1zer3"
    And Initial Configuration is submitted
    And Server id exist warning is confirmed
    Then Clients Tab is present

