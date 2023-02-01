#@Modifying
@ConfigurationInfo
Feature: Configuration Info APIs

  Scenario: View internal configuration
    * INTERNAL configuration parts exists
    * INTERNAL configuration source anchor info exists
    * INTERNAL configuration source global download url exists

  Scenario: View external configuration
    * EXTERNAL configuration parts exists
    * EXTERNAL configuration source anchor info exists
    * EXTERNAL configuration source global download url exists

  Scenario: Download configuration part
    * User can download EXTERNAL configuration part SHARED-PARAMETERS version 2
    * User can download INTERNAL configuration part PRIVATE-PARAMETERS version 2
