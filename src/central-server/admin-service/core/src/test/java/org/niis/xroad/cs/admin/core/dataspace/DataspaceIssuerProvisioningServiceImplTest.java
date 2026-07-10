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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.issuer.provisioning.proto.CredentialMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceIssuerProvisioningServiceImplTest {

    private static final String HOST = "issuer.example.test";
    private static final int DID_PORT = 6183;
    private static final int ISSUANCE_PORT = 6185;
    private static final String SCHEMA_URL = "https://example.com/schema/XRoadMembershipCredential.json";

    @Mock
    private IssuerProvisioningRpcClient rpcClient;
    @Mock
    private DataspaceIssuerProperties properties;

    private DataspaceIssuerProvisioningServiceImpl service;

    @BeforeEach
    void setUp() {
        when(properties.getHost()).thenReturn(HOST);
        when(properties.getDidPort()).thenReturn(DID_PORT);
        when(properties.getIssuancePort()).thenReturn(ISSUANCE_PORT);
        when(properties.getCredentialJsonSchemaUrl()).thenReturn(SCHEMA_URL);
        service = new DataspaceIssuerProvisioningServiceImpl(rpcClient, properties);
    }

    @Test
    void provisionIssuerEncodesPortWithPercentEncodingInDid() {
        service.provisionIssuer();

        ArgumentCaptor<String> didCaptor = ArgumentCaptor.forClass(String.class);
        verify(rpcClient).createParticipantContext(anyString(), didCaptor.capture(), anyString(), anyString(), anyString());

        String did = didCaptor.getValue();
        assertThat(did).startsWith("did:web:");
        assertThat(did).contains("%3A" + DID_PORT);
        assertThat(did).doesNotContain(":" + DID_PORT);
        assertThat(did).endsWith(":issuer");
        assertThat(did).isEqualTo("did:web:" + HOST + "%3A" + DID_PORT + ":issuer");
    }

    @Test
    void provisionIssuerBuildsKeyIdFromDid() {
        service.provisionIssuer();

        ArgumentCaptor<String> didCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(rpcClient).createParticipantContext(anyString(), didCaptor.capture(), anyString(), keyIdCaptor.capture(), anyString());

        assertThat(keyIdCaptor.getValue()).isEqualTo(didCaptor.getValue() + "#key-1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void provisionIssuerCreatesCredentialDefinitionWithCorrectShape() {
        service.provisionIssuer();

        ArgumentCaptor<String> formatCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> validityCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<List<String>> attestationsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<CredentialMapping>> mappingsCaptor = ArgumentCaptor.forClass(List.class);

        verify(rpcClient).createCredentialDefinition(
                anyString(), anyString(), anyString(),
                formatCaptor.capture(),
                anyString(), anyString(),
                validityCaptor.capture(),
                attestationsCaptor.capture(),
                mappingsCaptor.capture()
        );

        assertThat(formatCaptor.getValue()).isEqualTo("VC1_0_JWT");
        assertThat(validityCaptor.getValue()).isEqualTo(2592000L);
        assertThat(attestationsCaptor.getValue()).containsExactly("xroad-membership-attestation-definition");

        List<CredentialMapping> mappings = mappingsCaptor.getValue();
        assertThat(mappings).hasSize(4);
        assertThat(mappings).extracting(CredentialMapping::getInput)
                .containsExactly("membershipType", "xroadInstance", "memberClass", "memberCode");
        assertThat(mappings).extracting(CredentialMapping::getOutput)
                .allMatch(o -> o.startsWith("credentialSubject."));
        assertThat(mappings).extracting(CredentialMapping::getRequired)
                .containsOnly(true);
    }

    @Test
    void provisionIssuerCreatesAttestationDefinitionWithHolderType() {
        service.provisionIssuer();

        verify(rpcClient).createAttestationDefinition(
                anyString(),
                eq("xroad-membership-attestation-definition"),
                eq("holder")
        );
    }

    @Test
    void provisionIssuerReturnsIssuedStatus() {
        String result = service.provisionIssuer();

        assertThat(result).isEqualTo("ISSUED");
    }
}
