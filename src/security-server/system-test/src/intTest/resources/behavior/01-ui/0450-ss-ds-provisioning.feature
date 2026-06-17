# Numbered 0450 so it runs before the client-lifecycle features (0500+). With DSP enabled, the
# clientReg/clientDisable/clientRename management requests are routed through the dataspace and
# require the security server's participant context to already exist on the control plane; if
# provisioning has not run yet, their asset-access acquisition fails and no provider-side
# operational-data records are produced.
@SecurityServer
@DataSpaces
Feature: 0450 - SS: Data space provisioning

  Scenario: Issuer Service is provisioned
    Given Issuer Service participant context "issuer" with DID "did:web:ds-issuer-service%3A6183:issuer" is initialized and keypair is generated with private key alias "issuer-key"
    And Attestation definition "xroad-membership-attestation-definition" of type "holder" is created in issuer service participant "issuer"
    And Credential definition "xroad-membership-credential-definition" of type "XRoadMembershipCredential" with format "VC1_0_JWT" is created in issuer service participant "issuer" with attestation "xroad-membership-attestation-definition"

  Scenario: Identity Hub and Control Plane are provisioned and membership credential is issued
    When Data space provisioning is requested on the security server
    Then Data space provisioning status is "ISSUED"
