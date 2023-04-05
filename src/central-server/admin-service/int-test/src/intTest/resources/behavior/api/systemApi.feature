@SecurityServer
Feature: System API

  Scenario: Verifying that system status is OK
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    When system status is requested
    Then system status is validated

  Scenario: Verify system version endpoint works
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    Then system version endpoint returns version

  @Modifying
  Scenario: Update central server address
    Given Authentication header is set to SECURITY_OFFICER
    Then updating central server address with 'valid.url' should succeed
    And updating central server address with url 'invalid...address.c' should fail

  @Modifying
  Scenario: Check if history table contains valid ha node name after parameter update
    Given Authentication header is set to SECURITY_OFFICER
    When updating central server address with 'valid.url' should succeed
    Then history entry has node name 'test_node' for system parameter value 'valid.url'
