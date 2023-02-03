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

  Scenario: Download internal configuration anchor
    When user downloads INTERNAL configuration source anchor
    Then it should return internal configuration source anchor file

  Scenario: Download external configuration anchor
    When user downloads EXTERNAL configuration source anchor
    Then it should return external configuration source anchor file

  Scenario: Download configuration part
    * User can download EXTERNAL configuration part SHARED-PARAMETERS version 2
    * User can download INTERNAL configuration part PRIVATE-PARAMETERS version 2

  @Modifying
  Scenario: Uploading optional configuration part
    Given INTERNAL configuration part OPTIONAL-CONFIGURATION-PART-1 was not uploaded
    When user uploads INTERNAL configuration OPTIONAL-CONFIGURATION-PART-1 file monitoring-params_upload.xml
    Then INTERNAL configuration part OPTIONAL-CONFIGURATION-PART-1 is updated

  Scenario: Uploading unknown configuration part fails
    * INTERNAL configuration part NOT-EXISTING file upload fails
    * EXTERNAL configuration part NOT-EXISTING file upload fails
    * EXTERNAL configuration part PRIVATE-PARAMETERS file upload fails
    * EXTERNAL configuration part OPTIONAL-CONFIGURATION-PART-1 file upload fails

