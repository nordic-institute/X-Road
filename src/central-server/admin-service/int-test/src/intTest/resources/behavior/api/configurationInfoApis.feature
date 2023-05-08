@ConfigurationInfo
Feature: Configuration Info API

  Background:
    Given Authentication header is set to SECURITY_OFFICER

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
    Then it should return internal configuration source anchor file with filename "configuration_anchor_CS_internal_UTC_2022-01-01_01_00_00.xml"

  Scenario: Download external configuration anchor
    When user downloads EXTERNAL configuration source anchor
    Then it should return external configuration source anchor file with filename "configuration_anchor_CS_external_UTC_2022-01-01_01_00_00.xml"

  Scenario: Download configuration part
    * User can download EXTERNAL configuration part SHARED-PARAMETERS version 2
    * User can download INTERNAL configuration part PRIVATE-PARAMETERS version 2

  @Modifying
  Scenario: Uploading optional configuration part
    Given INTERNAL configuration part MONITORING was not uploaded
    When user uploads INTERNAL configuration MONITORING file monitoring-params_upload.xml
    Then INTERNAL configuration part MONITORING is updated

  Scenario: Uploading unknown configuration part fails
    * INTERNAL configuration part NOT-EXISTING file upload fails
    * EXTERNAL configuration part NOT-EXISTING file upload fails
    * EXTERNAL configuration part PRIVATE-PARAMETERS file upload fails
    * EXTERNAL configuration part FETCHINTERVAL file upload fails
    * EXTERNAL configuration part MONITORING file upload fails
