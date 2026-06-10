@SecurityServer
@UI
@ApiKeys
Feature: 0360 - SS: TLS key

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret123!
    And Keys and certificates tab is selected
    And Security Server TLS Key sub-tab is selected
