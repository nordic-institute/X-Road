@CentralServer
@ConfigurationParts
Feature: 0810 - CS: Global configuration: Internal configuration: Configuration parts

  Background:
    Given CentralServer login page is open
    And User xrd logs in to CentralServer with password secret
    And Global configuration tab is selected
    And Internal configuration sub-tab is selected

  Scenario Outline: User can download non optional <content-identifier> configuration
    Given There is entry for configuration part: <content-identifier>
    And Configuration part is generated
    And User can download it
    When User clicks download button for it
    Then Configuration part file is successfully downloaded
    Examples:
      | content-identifier |
      | SHARED-PARAMETERS  |
      | PRIVATE-PARAMETERS |

  Scenario Outline: User can't upload non optional configuration parts: <content-identifier>
    When There is entry for configuration part: <content-identifier>
    Then User can't upload configuration file for it
    Examples:
      | content-identifier |
      | SHARED-PARAMETERS  |
      | PRIVATE-PARAMETERS |

  Scenario Outline: User can upload optional configuration part for <content-identifier>
    Given There is entry for configuration part: <content-identifier>
    And User can't download it
    And User can upload configuration file for it
    When User uploads file <configuration-file> for it
    Then Configuration part was updated
    When User uploads file <configuration-file> for it
    Then Configuration part was updated
    Examples:
      | content-identifier | configuration-file             |
      | MONITORING         | monitoring-params_upload.xml   |
      | FETCHINTERVAL      | valid-fetchinterval-params.xml |
      | NEXTUPDATE         | valid-nextupdate-params.xml    |

  Scenario Outline: User can download optional <content-identifier> configuration
    Given There is entry for configuration part: <content-identifier>
    And User can download it
    When User clicks download button for it
    Then Configuration part file is successfully downloaded
    Examples:
      | content-identifier |
      | MONITORING         |
      | FETCHINTERVAL      |
      | NEXTUPDATE         |
