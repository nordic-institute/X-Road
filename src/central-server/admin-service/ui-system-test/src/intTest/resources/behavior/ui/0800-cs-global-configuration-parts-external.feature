@CentralServer
@ConfigurationParts
@LoadingTesting
Feature: CS: Global configuration: External configuration: Configuration parts

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And External configuration sub-tab is selected

  Scenario Outline: User can upload configuration part for <content-identifier>
    Given There is entry for configuration part: <content-identifier>
    And Configuration part doesn't have update date
    And User can't download it
    And User can upload configuration file for it
    When User uploads file <configuration-file> for it
    Then Configuration part has update date
    Examples:
      | content-identifier | configuration-file |
      | SHARED-PARAMETERS  | file.xml           |

  Scenario Outline: User can download <content-identifier> configuration
    Given There is entry for configuration part: <content-identifier>
    And Configuration part has update date
    And User can download it
    When User clicks download button for it
    Then Configuration part file is successfully downloaded
    Examples:
      | content-identifier |
      | SHARED-PARAMETERS  |
