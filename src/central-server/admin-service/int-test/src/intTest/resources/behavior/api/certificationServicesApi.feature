@CertificationServices
Feature: Certification services API

  @Modifying
  Scenario: Certification Services are created and listed
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    And Certification service with name "cert2" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider" is created
    When Certification services are listed
    Then Certification services are as follows
      | $id | $name |
      | 1   | cert1 |
      | 2   | cert2 |

  @Modifying
  Scenario: Certification Service is created and retrieved
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service with id 1 is retrieved
    Then Certification service is as follows
      | $id                       | 1                                                                               |
      | $name                     | cert1                                                                           |
      | $issuerDistinguishedName  | EMAILADDRESS=aaa@bbb.ccc, CN=Cyber, OU=ITO, O=Cybernetica, C=EE                 |
      | $subjectDistinguishedName | CN=cert1                                                                        |
      | $notAfter                 | [not_null]                                                                      |
      | $notBefore                | [not_null]                                                                      |
      | $certificateProfileInfo   | ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider |
      | $tlsAuth                  | false                                                                           |
      | $created_at               | [not_null]                                                                      |
      | $updated_at               | [not_null]                                                                      |

  Scenario Outline: Certification Service creation failed when incorrect certificateProfileInfo is used
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "<$certName>" and certificateProfileInfo "<$certificateProfileInfo>" is created
    Then Response is of status code 400 and error code "<$errorCode>"
    Examples:
      | $certName | $certificateProfileInfo | $errorCode                               |
      |           |                         | certificate_profile_info_class_not_found |
      | cert1     | missing.class           | certificate_profile_info_class_not_found |

  @Modifying
  Scenario: Certification Service get fails due to wrong id
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service with id 10 is retrieved
    Then Response is of status code 404 and error code "certification_service_not_found"

  @Modifying
  Scenario: Certification Service is created and updated
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service with id 1 is updated with tlsAuth true and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"
    Then Returned certification service has id 1, tlsAuth true and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"

  @Modifying
  Scenario Outline: Certification Service update failed
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service with id <$id> is updated with tlsAuth <$tlsAuth> and certificateProfileInfo "<$certificateProfileInfo>"
    Then Response is of status code <$statusCode> and error code "<$errorCode>"
    Examples:
      | $id | $tlsAuth | $certificateProfileInfo                                                         | $errorCode                               | $statusCode |
      | 1   | false    |                                                                                 | certificate_profile_info_class_not_found | 400         |
      | 1   | false    | missing.class                                                                   | certificate_profile_info_class_not_found | 400         |
      | 2   | false    | ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider | certification_service_not_found          | 404         |

  Scenario: Certification Services listing is forbidden for non privileged user
    Given Authentication header is set to SECURITY_OFFICER
    When Certification services are listed
    Then Response is of status code 403

  @Modifying
  Scenario: Certification Service is created with OCSP responders and deleted
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    And OCSP responder with url "https://test.com" is added to certification service with id 1
    And OCSP responder with url "https://test-2.com" is added to certification service with id 1
    When Certification service with id 1 OCSP responders are listed
    Then Certification service OCSP responders are as follows
      | $id | $url               | $hasCertificate |
      | 1   | https://test.com   | true            |
      | 2   | https://test-2.com | true            |
    When certification service is deleted
    And certification service is retrieved
    Then Response is of status code 404 and error code "certification_service_not_found"

  @Modifying
  Scenario: Certification service deletion
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    And OCSP responder with url "https://test.com" is added to certification service with id 1
    And OCSP responder with url "https://test-2.com" is added to certification service with id 1
    And intermediate CA added to certification service
    And OCSP responder is added to intermediate CA
    And intermediate CA added to certification service
    When certification service is deleted
    And certification service is retrieved
    Then Response is of status code 404 and error code "certification_service_not_found"

  @Modifying
  Scenario: Certification Service delete fails due to wrong id
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service with id 999 is deleted
    Then Response is of status code 404 and error code "certification_service_not_found"

  @Modifying
  Scenario: Certification Service get ocsp responders fails due to wrong id
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service with id 10 OCSP responders are listed
    Then Response is of status code 404 and error code "certification_service_not_found"

  @Modifying
  Scenario: Certification Service certificate is viewed
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service certificate with id 1 is retrieved
    Then Certification service certificate is as follows
      | $hash                     | [not_null]                                                      |
      | $issuerCommonName         | Cyber                                                           |
      | $issuerDistinguishedName  | EMAILADDRESS=aaa@bbb.ccc, CN=Cyber, OU=ITO, O=Cybernetica, C=EE |
      | $keyUsages                | DIGITAL_SIGNATURE                                               |
      | $notAfter                 | [not_null]                                                      |
      | $notBefore                | [not_null]                                                      |
      | $publicKeyAlgorithm       | RSA                                                             |
      | $rsaPublicKeyExponent     | [not_null]                                                      |
      | $rsaPublicKeyModulus      | [not_null]                                                      |
      | $serial                   | 1                                                               |
      | $signature                | [not_null]                                                      |
      | $signatureAlgorithm       | SHA256withRSA                                                   |
      | $subjectCommonName        | cert1                                                           |
      | $subjectDistinguishedName | CN=cert1                                                        |
      | $version                  | 3                                                               |

  @Modifying
  Scenario: Certification Service certificate get fails due to wrong id
    Given Authentication header is set to SYSTEM_ADMINISTRATOR
    And Certification service with name "cert1" and certificateProfileInfo "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider" is created
    When Certification service certificate with id 10 is retrieved
    Then Response is of status code 404 and error code "certification_service_not_found"
