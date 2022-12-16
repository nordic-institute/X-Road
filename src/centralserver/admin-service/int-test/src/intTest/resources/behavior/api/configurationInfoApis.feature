@Modifying
@ConfigurationInfo
Feature: Configuration Info APIs

  Scenario: View internal configuration
    When internal configuration parts exists
    And internal configuration source anchor info exists
    Then internal configuration source global download url exists
