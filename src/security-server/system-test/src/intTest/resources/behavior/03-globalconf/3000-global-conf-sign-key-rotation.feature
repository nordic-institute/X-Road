@SecurityServer
@GlobalConf
Feature: 3000 - SS: Global Conf

  Scenario: Global conf sign keys rotation
    Given Security Server's global conf expiration date is equal to 2033-11-28T21:39:05Z
    When Central Server's global conf is updated by a new active signing key
    Then Security Server's global conf expiration date is equal to 2033-11-28T21:40:05Z
