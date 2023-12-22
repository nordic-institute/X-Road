@ManagementServices
Feature: Management services API

  @Modifying
  Scenario: Update management services configuration is successful
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:member-for-management' is added
    And new subsystem "CS:E2E:member-for-management:Management" is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-for-management:SS0' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And client 'CS:E2E:member-for-management:Management' is registered as security server 'CS:E2E:member-for-management:SS0' client from 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to SECURITY_OFFICER
    When Management services provider id is set to "CS:E2E:member-for-management:Management"
    Then Management services configuration is as follows
      | $securityServerId                    | SERVER:CS:E2E:member-for-management:SS0           |
      | $securityServerOwnersGlobalGroupCode | security-server-owners                            |
      | $serviceProviderName                 | Member name for CS:E2E:member-for-management      |
      | $servicesAddress                     | https://cs:4002/managementservice/manage/         |
      | $wsdlAddress                         | http://cs/managementservices.wsdl                 |
      | $serviceProviderId                   | SUBSYSTEM:CS:E2E:member-for-management:Management |

  @Modifying
  Scenario: Update management services configuration is successful using dedicated endpoint
    Given Authentication header is set to SECURITY_OFFICER
    And member class 'E2E' is created
    And Authentication header is set to REGISTRATION_OFFICER
    And new member 'CS:E2E:member-for-management' is added
    And new subsystem "CS:E2E:member-for-management:Management" is added
    And Authentication header is set to MANAGEMENT_SERVICE
    And new security server 'CS:E2E:member-for-management:SS0' authentication certificate registered with origin 'SECURITY_SERVER'
    And management request is approved
    And Authentication header is set to SECURITY_OFFICER
    When Management services provider id is set to "CS:E2E:member-for-management:Management"
    And security server 'CS:E2E:member-for-management:SS0' is registered as management service provider
    Then Management services configuration is as follows
      | $securityServerId                    | SERVER:CS:E2E:member-for-management:SS0           |
      | $securityServerOwnersGlobalGroupCode | security-server-owners                            |
      | $serviceProviderName                 | Member name for CS:E2E:member-for-management      |
      | $servicesAddress                     | https://cs:4002/managementservice/manage/         |
      | $wsdlAddress                         | http://cs/managementservices.wsdl                 |
      | $serviceProviderId                   | SUBSYSTEM:CS:E2E:member-for-management:Management |

  Scenario: Update management services configuration is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Management services provider id is set to "CS:E2E:member-for-management:Management"
    Then Response is of status code 403

  Scenario: Update management services configuration fails, missing subsystem
    Given Authentication header is set to SECURITY_OFFICER
    When Management services provider id is set to "CS:E2E:member-for-management:Random"
    Then Response is of status code 404 and error code "subsystem_not_found"

  Scenario: Update management services configuration fails, not a subsystem
    Given Authentication header is set to SECURITY_OFFICER
    When Management services provider id is set to "CS:E2E:member-for-management"
    Then Response is of status code 400 and error code "invalid_service_provider_id"

  Scenario: Get management services configuration is successful on freshly initialized instance
    Given Authentication header is set to SECURITY_OFFICER
    When Management services configuration is retrieved
    Then Management services configuration is as follows
      | $securityServerId                    |                                           |
      | $securityServerOwnersGlobalGroupCode | security-server-owners                    |
      | $serviceProviderName                 |                                           |
      | $servicesAddress                     | https://cs:4002/managementservice/manage/ |
      | $wsdlAddress                         | http://cs/managementservices.wsdl         |
      | $serviceProviderId                   |                                           |

  Scenario: Get management services configuration is forbidden for non privileged user
    Given Authentication header is set to REGISTRATION_OFFICER
    When Management services configuration is retrieved
    Then Response is of status code 403

  Scenario: Get management service TLS certificate is retrieved for privileged user
    Given Authentication header is set to SECURITY_OFFICER
    When Management service TLS certificate is retrieved
    Then Response is of status code 200

  Scenario: Management service TLS certificate is successfully downloaded for privileged user
    Given Authentication header is set to SECURITY_OFFICER
    When Management service TLS certificate is downloaded
    Then Response is of status code 200

  @Modifying
  Scenario: Management service TLS key and certificate is successfully reCreated for privileged user
    Given Authentication header is set to SECURITY_OFFICER
    When Management service TLS key and certificate is created
    Then Response is of status code 201

  Scenario: Management service certificate sign request is successfully created for privileged user
    Given Authentication header is set to SECURITY_OFFICER
    When Management service certificate CSR is generated
    Then Response is of status code 200

  @Modifying
  Scenario: Management service TLS certificate is successfully uploaded for privileged user
    Given Authentication header is set to SECURITY_OFFICER
    When Management service TLS certificate is uploaded
    Then Response is of status code 200
