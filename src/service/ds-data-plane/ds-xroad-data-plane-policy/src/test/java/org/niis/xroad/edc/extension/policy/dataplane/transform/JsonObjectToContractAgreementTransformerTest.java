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

package org.niis.xroad.edc.extension.policy.dataplane.transform;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.transform.spi.InvalidPropertyBuilder;
import org.eclipse.edc.transform.spi.MissingPropertyBuilder;
import org.eclipse.edc.transform.spi.ProblemBuilder;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_ASSET_ID;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_CONSUMER_ID;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_POLICY;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_PROVIDER_ID;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_SIGNING_DATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonObjectToContractAgreementTransformerTest {

    @Mock
    private TransformerContext context;
    @Mock
    private ProblemBuilder problemBuilder;
    @Mock
    private InvalidPropertyBuilder invalidPropertyBuilder;
    @Mock
    private MissingPropertyBuilder missingPropertyBuilder;

    private JsonObjectToContractAgreementTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new JsonObjectToContractAgreementTransformer();
    }

    @Test
    void transformValidObjectReturnsContractAgreement() {
        var policy = mock(Policy.class);
        when(context.transform(any(JsonObject.class), eq(Policy.class))).thenReturn(policy);

        var result = transformer.transform(buildValidJsonObject(), context);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("agreement-123");
        assertThat(result.getConsumerId()).isEqualTo("consumer-1");
        assertThat(result.getProviderId()).isEqualTo("provider-1");
        assertThat(result.getAssetId()).isEqualTo("asset-1");
        assertThat(result.getContractSigningDate()).isEqualTo(1234567890);
        assertThat(result.getPolicy()).isSameAs(policy);
    }

    @Test
    void transformNullPolicyReturnsNull() {
        when(context.transform(any(JsonObject.class), eq(Policy.class))).thenReturn(null);
        stubInvalidPropertyChain();

        var result = transformer.transform(buildValidJsonObject(), context);

        assertThat(result).isNull();
    }

    @Test
    void transformMissingIdReturnsNull() {
        var policy = mock(Policy.class);
        when(context.transform(any(JsonObject.class), eq(Policy.class))).thenReturn(policy);
        stubMissingPropertyChain();

        var object = Json.createObjectBuilder()
                .add(CONTRACT_AGREEMENT_CONSUMER_ID, Json.createValue("consumer-1"))
                .add(CONTRACT_AGREEMENT_PROVIDER_ID, Json.createValue("provider-1"))
                .add(CONTRACT_AGREEMENT_POLICY, Json.createObjectBuilder().build())
                .add(CONTRACT_AGREEMENT_ASSET_ID, Json.createValue("asset-1"))
                .add(CONTRACT_AGREEMENT_SIGNING_DATE, Json.createValue(1234567890))
                .build();

        var result = transformer.transform(object, context);

        assertThat(result).isNull();
    }

    private void stubInvalidPropertyChain() {
        when(context.problem()).thenReturn(problemBuilder);
        when(problemBuilder.invalidProperty()).thenReturn(invalidPropertyBuilder);
        when(invalidPropertyBuilder.type(anyString())).thenReturn(invalidPropertyBuilder);
        when(invalidPropertyBuilder.property(anyString())).thenReturn(invalidPropertyBuilder);
    }

    private void stubMissingPropertyChain() {
        when(context.problem()).thenReturn(problemBuilder);
        when(problemBuilder.missingProperty()).thenReturn(missingPropertyBuilder);
        when(missingPropertyBuilder.type(anyString())).thenReturn(missingPropertyBuilder);
        when(missingPropertyBuilder.property(anyString())).thenReturn(missingPropertyBuilder);
    }

    private JsonObject buildValidJsonObject() {
        return Json.createObjectBuilder()
                .add("@id", "agreement-123")
                .add(CONTRACT_AGREEMENT_CONSUMER_ID, Json.createValue("consumer-1"))
                .add(CONTRACT_AGREEMENT_PROVIDER_ID, Json.createValue("provider-1"))
                .add(CONTRACT_AGREEMENT_POLICY, Json.createObjectBuilder().build())
                .add(CONTRACT_AGREEMENT_ASSET_ID, Json.createValue("asset-1"))
                .add(CONTRACT_AGREEMENT_SIGNING_DATE, Json.createValue(1234567890))
                .build();
    }
}
