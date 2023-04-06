@History
Feature: DB changes history functionality

  @Modifying
  Scenario: Check if history table contains valid ha node and user name after parameter update
    Given Authentication header is set to SECURITY_OFFICER
    When updating central server address with 'valid.url' should succeed
    Then history entry has node name 'test_node' for system parameter value 'valid.url'
    And history entry has user name 'api-key-3' for system parameter value 'valid.url'
