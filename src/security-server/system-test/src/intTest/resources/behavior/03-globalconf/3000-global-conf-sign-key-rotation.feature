@SecurityServer
@GlobalConf
Feature: 3000 - SS: Global Conf

  Scenario: Global conf sign keys rotation
    Given Security Server's global conf expiration date is equal to 2034-04-09T00:36:51Z
    When Central Server's global conf is updated by a new active signing key
    Then Security Server's global conf expiration date is equal to 2034-04-09T00:37:51Z
