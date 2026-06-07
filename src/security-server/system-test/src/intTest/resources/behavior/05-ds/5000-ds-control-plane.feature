@SecurityServer
@DataSpaces
Feature: 5000 - DS control plane tests

  Scenario: Issuer Service is provisioned
    Given Issuer Service participant context "issuer" with DID "did:web:ds-issuer-service%3A6183:issuer" is initialized and keypair is generated with private key alias "issuer-key"
    And Attestation definition "xroad-membership-attestation-definition" of type "holder" is created in issuer service participant "issuer"
    And Credential definition "xroad-membership-credential-definition" of type "MembershipCredential" with format "VC1_0_JWT" is created in issuer service participant "issuer" with attestation "xroad-membership-attestation-definition"

  Scenario: Identity Hub and Control Plane are provisioned and membership credential is issued
    When Data space provisioning is requested on the security server
    Then Data space provisioning status is "ISSUED"

  Scenario: Catalog can be retrieved over DSP protocol
    Then Catalog can be retrieved using participant context "ss0" with DID "did:web:ds-identity-hub%3A7183"
    And Participant context "ss0" can be retrieved
