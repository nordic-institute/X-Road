@Modifying
@ConfigurationInfo
Feature: Configuration Info APIs

  Scenario: View internal configuration
    When INTERNAL configuration parts exists
    And INTERNAL configuration source anchor info exists
    Then INTERNAL configuration source global download url exists

  Scenario: View external configuration
    When EXTERNAL configuration parts exists
    And EXTERNAL configuration source anchor info exists
    Then EXTERNAL configuration source global download url exists