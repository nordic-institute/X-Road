@SecurityServer
@Client
Feature: 0510 - SS: Client Local groups

  Background:
    Given SecurityServer login page is open
    And Page is prepared to be tested
    And User xrd logs in to SecurityServer with password secret
    And Clients tab is selected


  Scenario Outline: Local group <$groupCode> is added to TestService
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group "<$groupCode>" with description "<$groupDesc>" is added
    Then Local group "<$groupCode>" is present in the list
    Examples:
      | $groupCode | $groupDesc |
      | group-1    | desc-100   |
      | group-2    | desc-200   |
      | group-3    | desc       |
      | aaa-1      | desc-300   |
      | bbb-1      | desc-400   |
      | yyy-1      | none       |

  Scenario: Local group is not added as it already exists
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group "group-1" with description "desc" is added
    Then error: "Local group code already exists" was displayed

  Scenario: Local groups are sorted by default
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    Then Local group table is ordered as follows:
      | aaa-1   |
      | bbb-1   |
      | group-1 |
      | group-2 |
      | group-3 |
      | yyy-1   |

  Scenario: Local groups are sorted by Description
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group table sorting change to "Description" column
    Then Local group table is ordered as follows:
      | group-3 |
      | group-1 |
      | group-2 |
      | aaa-1   |
      | bbb-1   |
      | yyy-1   |

  Scenario: Local groups are filtered to "group"
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group filter is set to "group"
    Then Local group table is ordered as follows:
      | group-1 |
      | group-2 |
      | group-3 |

  Scenario: Local groups are filtered to "aaa-1"
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group filter is set to "aaa-1"
    Then Local group table is ordered as follows:
      | aaa-1 |

  Scenario: Local group aaa-1 is deleted
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group "aaa-1" is selected
    Then Local group is deleted
    Then Local group table is ordered as follows:
      | bbb-1   |
      | group-1 |
      | group-2 |
      | group-3 |
      | yyy-1   |

  Scenario: Local group group-1 is edited
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group "group-1" is selected
    And Local group description is set to ""
    Then Form shows an error "The Description field is required"
    And Local group description is set to "edited"
    When Local group search dialog is opened and members for instance "CS" and member class "GOV" are filtered
    And Following members are added to local group:
      | CS:GOV:0245437-2:random-sub-1  |
      | CS:GOV:0245437-2:test-consumer |
    Then Following members are present in local group:
      | CS:GOV:0245437-2:random-sub-1  |
      | CS:GOV:0245437-2:test-consumer |

  Scenario: Local group group-1 member is removed
    When Client "TestService" is opened
    And Local groups sub-tab is selected
    And Local group "group-1" is selected
    And Local group member "CS:GOV:0245437-2:test-consumer" is removed
    Then Following members are missing in local group:
      | CS:GOV:0245437-2:test-consumer |
