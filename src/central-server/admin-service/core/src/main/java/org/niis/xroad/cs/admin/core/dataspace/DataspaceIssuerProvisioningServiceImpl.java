/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.dataspace;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.edc.issuer.provisioning.proto.CredentialMapping;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Provisions the data space issuer over gRPC. The X-Road membership credential definition shape is
 * fixed (constants below); only the issuer host/ports and the published schema URL are configurable.
 */
@Service
@RequiredArgsConstructor
public class DataspaceIssuerProvisioningServiceImpl implements DataspaceIssuerProvisioningService {

    private static final String ISSUER_PARTICIPANT_ID = "issuer";
    private static final String ISSUER_KEY_ALIAS = "issuer-key";
    private static final String ATTESTATION_DEFINITION_ID = "xroad-membership-attestation-definition";
    private static final String ATTESTATION_TYPE = "holder";
    private static final String CREDENTIAL_DEFINITION_ID = "xroad-membership-credential-definition";
    private static final String CREDENTIAL_TYPE = "XRoadMembershipCredential";
    private static final String CREDENTIAL_FORMAT = "VC1_0_JWT";
    private static final String CREDENTIAL_JSON_SCHEMA = "{}";
    private static final long CREDENTIAL_VALIDITY_SECONDS = 2592000L;
    private static final String CREDENTIAL_SUBJECT_PREFIX = "credentialSubject.";
    private final IssuerProvisioningRpcClient rpcClient;
    private final DataspaceIssuerProperties properties;

    @Override
    public void provisionIssuer() {
        String did = "did:web:" + properties.getHost() + "%3A" + properties.getDidPort() + ":issuer";
        String issuerServiceUrl = "https://%s:%d/api/issuance/v1beta/participants/issuer"
                .formatted(properties.getHost(), properties.getIssuancePort());
        String keyId = did + "#key-1";

        rpcClient.createParticipantContext(ISSUER_PARTICIPANT_ID, did, issuerServiceUrl, keyId, ISSUER_KEY_ALIAS);
        rpcClient.createAttestationDefinition(ISSUER_PARTICIPANT_ID, ATTESTATION_DEFINITION_ID, ATTESTATION_TYPE);
        rpcClient.createCredentialDefinition(ISSUER_PARTICIPANT_ID, CREDENTIAL_DEFINITION_ID, CREDENTIAL_TYPE,
                CREDENTIAL_FORMAT, CREDENTIAL_JSON_SCHEMA, properties.getCredentialJsonSchemaUrl(),
                CREDENTIAL_VALIDITY_SECONDS, List.of(ATTESTATION_DEFINITION_ID), membershipMappings());
    }

    private List<CredentialMapping> membershipMappings() {
        return List.of(
                membershipMapping("membershipType"),
                membershipMapping("xroadInstance"),
                membershipMapping("memberClass"),
                membershipMapping("memberCode"));
    }

    private CredentialMapping membershipMapping(String field) {
        return CredentialMapping.newBuilder()
                .setInput(field)
                .setOutput(CREDENTIAL_SUBJECT_PREFIX + field)
                .setRequired(true)
                .build();
    }
}
