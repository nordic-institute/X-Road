Feature: 020 - CS: EDC

  Background:
    Given Environment is initialized

  @DataSpace
  Scenario: Federated catalog is responsive
    When Federated catalog is queried
    """json
    {}
    """
    Then response is sent of http status code 200
