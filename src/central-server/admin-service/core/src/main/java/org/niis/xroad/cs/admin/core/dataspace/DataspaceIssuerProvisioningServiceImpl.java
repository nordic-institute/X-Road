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
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.cs.admin.api.domain.CanSignOutcome;
import org.niis.xroad.cs.admin.api.domain.CanSignResult;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.edc.issuer.provisioning.proto.CredentialMapping;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_DID_DRIFT;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;

/**
 * Provisions the data space issuer over gRPC. The X-Road membership credential definition shape is
 * fixed (constants below); only the issuer host/ports and the published schema URL are configurable.
 *
 * <p>Guards the issuer's signing key: a key is created only when the issuer cannot already sign for the
 * intended DID, and the identity is bound ({@link DataspaceIssuerDidService}) only after a final
 * "can sign" check passes. This lets a run stopped between key creation and binding finish safely, while
 * never creating or replacing a key for an issuer that can sign for a different DID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataspaceIssuerProvisioningServiceImpl implements DataspaceIssuerProvisioningService {

    static final String ISSUER_PARTICIPANT_ID = "issuer";
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
    private final DataspaceIssuerDidService dataspaceIssuerDidService;

    @Override
    public void provisionIssuer() {
        String host = properties.getHost();
        String did = "did:web:" + host + "%3A" + properties.getDidPort() + ":issuer";
        String issuerServiceUrl = "https://%s:%d/api/issuance/v1/participants/issuer"
                .formatted(host, properties.getIssuancePort());
        String statuslistBaseUrl = "https://%s:%d/statuslist".formatted(host, properties.getStatuslistPort());
        String keyId = did + "#key-1";

        if (alreadyUsableFor(rpcClient.canSign(ISSUER_PARTICIPANT_ID), did)) {
            log.warn("Issuer participant context '{}' can already sign for DID '{}'; finishing a stopped set-up "
                    + "run without creating a new key", ISSUER_PARTICIPANT_ID, did);
        } else {
            rpcClient.createParticipantContext(ISSUER_PARTICIPANT_ID, did, issuerServiceUrl, keyId, ISSUER_KEY_ALIAS);
        }

        rpcClient.createAttestationDefinition(ISSUER_PARTICIPANT_ID, ATTESTATION_DEFINITION_ID, ATTESTATION_TYPE);
        rpcClient.createCredentialDefinition(ISSUER_PARTICIPANT_ID, CREDENTIAL_DEFINITION_ID, CREDENTIAL_TYPE,
                CREDENTIAL_FORMAT, CREDENTIAL_JSON_SCHEMA, properties.getCredentialJsonSchemaUrl(),
                CREDENTIAL_VALIDITY_SECONDS, List.of(ATTESTATION_DEFINITION_ID), membershipMappings());

        requireCanSignFor(rpcClient.canSign(ISSUER_PARTICIPANT_ID), did);

        dataspaceIssuerDidService.register(did, host, properties.getDidPort(), issuerServiceUrl, statuslistBaseUrl);
    }

    /**
     * @return {@code true} when the issuer can already sign for {@code did}, meaning a key exists and no new one
     * must be created; {@code false} when no participant context exists yet.
     * @throws XrdRuntimeException when the issuer cannot sign at all, or can sign only for a different DID
     */
    private boolean alreadyUsableFor(CanSignResult result, String did) {
        return switch (result.outcome()) {
            case ABSENT -> false;
            case CAN_SIGN -> requireSameDid(result, did);
            case CANNOT_SIGN -> throw provisioningFailed(
                    "issuer cannot sign for participant context '" + ISSUER_PARTICIPANT_ID + "' before "
                            + "set-up created anything: " + result.cause());
        };
    }

    private void requireCanSignFor(CanSignResult result, String did) {
        if (result.outcome() != CanSignOutcome.CAN_SIGN || !did.equals(result.did())) {
            throw provisioningFailed(
                    "issuer cannot sign for DID '" + did + "' after provisioning; outcome was " + result.outcome());
        }
    }

    private static XrdRuntimeException provisioningFailed(String details) {
        return XrdRuntimeException.systemException(DSP_PROVISIONING_FAILED)
                .details(details)
                .build();
    }

    private boolean requireSameDid(CanSignResult result, String did) {
        if (!did.equals(result.did())) {
            throw XrdRuntimeException.systemException(DSP_PARTICIPANT_DID_DRIFT)
                    .metadataItems(result.did(), did)
                    .details(("issuer can already sign for DID '%s', but the DID to provision is '%s'; refusing to "
                            + "touch the participant context until the drift is resolved").formatted(result.did(), did))
                    .build();
        }
        return true;
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
