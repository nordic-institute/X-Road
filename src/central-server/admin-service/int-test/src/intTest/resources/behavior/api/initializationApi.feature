@Initialization
@SkipInitialization
Feature: Initialization API

  @ResetDB
  @Modifying
  Scenario: Server initialization is successful
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Signer.initSoftwareToken is mocked to accept password
    When Server is initialized with address "e2e-cs", instance-identifier "E2E-CS", token pin "1234-VALID"
    And Signer.getToken response is mocked for active token "0"
    Then Server initialization status is requested
    And Server initialization status is as follows
      | $softwareTokenInitStatus | INITIALIZED |
      | $instanceIdentifier      | E2E-CS      |
      | $centralServerAddress    | e2e-cs      |

  @ResetDB
  @Modifying
  Scenario: Server initialization is successful, but is rejected second time
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Signer.initSoftwareToken is mocked to accept password
    When Server is initialized with address "e2e-cs", instance-identifier "E2E-CS", token pin "1234-VALID"
    And Signer.getToken response is mocked for active token "0"
    Then Server initialization status is requested
    And Server initialization status is as follows
      | $softwareTokenInitStatus | INITIALIZED |
      | $instanceIdentifier      | E2E-CS  |
      | $centralServerAddress    | e2e-cs  |
    When Server is initialized with address "e2e-cs", instance-identifier "E2E-CS", token pin "1234-VALID"
    Then Response is of status code 409 and error code "init_already_initialized"

  @ResetDB
  @Modifying
  Scenario: Server initialization is successful
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Signer.initSoftwareToken is mocked to accept password
    When Server is initialized with address "e2e-cs", instance-identifier "E2E-CS", token pin "1234-VALID"
    And Signer.getToken response is mocked for active token "0"
    Then Server initialization status is requested
    And Server initialization status is as follows
      | $softwareTokenInitStatus | INITIALIZED |
      | $instanceIdentifier      | E2E-CS      |
      | $centralServerAddress    | e2e-cs      |


  Scenario: Server initialization is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Server is initialized with address "e2e-cs", instance-identifier "E2E-CS", token pin "1234-VALID"
    Then Response is of status code 403


  Scenario Outline: Server initialization fails with error
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Signer.initSoftwareToken is mocked to accept password
    When Server is initialized with address "<$centralServerAddress>", instance-identifier "<$instanceIdentifier>", token pin "<$tokenPin>"
    Then Response is of status code 400 and error code "<$errorCode>"
    Examples:
      | $tokenPin  | $instanceIdentifier  | $centralServerAddress   | $errorCode         |
      | 12         | E2E-CS               | e2e-cs                  | token_weak_pin           |
      |            | E2E-CS               | e2e-cs                  | validation_failure |
      | 1234-VALID | INSTANCE::::%INVALID | e2e-cs                  | validation_failure |
      | 1234-VALID |                      | e2e-cs                  | validation_failure |
      | 1234-VALID | E2E-CS               | 123.123..invalid..123.x | validation_failure |
      | 1234-VALID | E2E-CS               |                         | validation_failure |

