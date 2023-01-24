@ConfigurationSourceAnchors
Feature: Configuration Source Anchor API

  @Modifying
  Scenario: Re-create internal configuration source anchor
    When user gets the "internal" configuration source anchor
    And user recreates the "internal" configuration source anchor
    Then recreated anchor matches returned from GET API
  @Modifying
  Scenario: Re-create external configuration source anchor
    When user gets the "external" configuration source anchor
    And user recreates the "external" configuration source anchor
    Then recreated anchor matches returned from GET API
