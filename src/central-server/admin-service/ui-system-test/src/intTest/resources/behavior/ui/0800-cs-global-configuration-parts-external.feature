@CentralServer
@ConfigurationParts
@LoadingTesting
Feature: 0800 - CS: Global configuration: External configuration: Configuration parts

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And External configuration sub-tab is selected

  Scenario Outline: User can download <content-identifier> configuration
    Given There is entry for configuration part: <content-identifier>
    And Configuration part is generated
    And User can download it
    When User clicks download button for it
    Then Configuration part file is successfully downloaded
    Examples:
      | content-identifier |
      | SHARED-PARAMETERS  |

  Scenario Outline: User can't upload internal configuration parts: <content-identifier>
    When There is entry for configuration part: <content-identifier>
    Then User can't upload configuration file for it
    Examples:
      | content-identifier |
      | SHARED-PARAMETERS  |
