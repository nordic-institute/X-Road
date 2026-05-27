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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContractDefinitionMapperTest {

    private static final String PARTICIPANT_CTX = "xroad-provider";

    private static final ServiceId.Conf SERVICE_1 = ServiceId.Conf.create("DEV", "GOV", "1234", "SubSys", "svc1", "v1");
    private static final ServiceId.Conf SERVICE_2 = ServiceId.Conf.create("DEV", "GOV", "5678", "SubSys", "svc2");

    private static final ClientId.Conf SUBJECT_CLIENT = ClientId.Conf.create("DEV", "GOV", "9999", "Consumer");
    private static final GlobalGroupId SUBJECT_GROUP = GlobalGroupId.Conf.create("DEV", "sec-owners");

    @Test
    void singleSubjectProducesCorrectIdFormat() {
        var result = ContractDefinitionMapper.toContractDefinition(SERVICE_1, SUBJECT_CLIENT, PARTICIPANT_CTX);

        assertThat(result.getId()).endsWith("-contract-definition");
        assertThat(result.getId()).isEqualTo("DEV:GOV:1234:SubSys:svc1:v1:DEV:GOV:9999:Consumer-contract-definition");
    }

    @Test
    void multiSubjectSameAssetProducesDistinctIds() {
        var resultClient = ContractDefinitionMapper.toContractDefinition(SERVICE_1, SUBJECT_CLIENT, PARTICIPANT_CTX);
        var resultGroup = ContractDefinitionMapper.toContractDefinition(SERVICE_1, SUBJECT_GROUP, PARTICIPANT_CTX);

        assertThat(resultClient.getId()).isNotEqualTo(resultGroup.getId());
        assertThat(resultClient.getId()).endsWith("-contract-definition");
        assertThat(resultGroup.getId()).endsWith("-contract-definition");
    }

    @Test
    void accessPolicyIdEqualsContractPolicyId() {
        var result = ContractDefinitionMapper.toContractDefinition(SERVICE_1, SUBJECT_CLIENT, PARTICIPANT_CTX);

        assertThat(result.getAccessPolicyId()).isEqualTo(result.getContractPolicyId());
    }

    @Test
    void policyIdIsSuffixStrippedContractId() {
        var result = ContractDefinitionMapper.toContractDefinition(SERVICE_1, SUBJECT_CLIENT, PARTICIPANT_CTX);

        var expectedPolicyId = result.getId().replace("-contract-definition", "");
        assertThat(result.getAccessPolicyId()).isEqualTo(expectedPolicyId);
    }

    @Test
    void assetsSelectorContainsCriterionWithEdcNamespaceId() {
        var result = ContractDefinitionMapper.toContractDefinition(SERVICE_1, SUBJECT_CLIENT, PARTICIPANT_CTX);

        assertThat(result.getAssetsSelector()).hasSize(1);
        var criterion = result.getAssetsSelector().getFirst();
        assertThat(criterion.getOperandLeft()).isEqualTo("https://w3id.org/edc/v0.0.1/ns/id");
        assertThat(criterion.getOperator()).isEqualTo("=");
        assertThat(criterion.getOperandRight()).isEqualTo("DEV:GOV:1234:SubSys:svc1:v1");
    }

    @Test
    void participantContextIdSetOnDefinition() {
        var result = ContractDefinitionMapper.toContractDefinition(SERVICE_1, SUBJECT_CLIENT, PARTICIPANT_CTX);

        assertThat(result.getParticipantContextId()).isEqualTo(PARTICIPANT_CTX);
    }

    @Test
    void fivePartServiceIdProducesValidDefinition() {
        var result = ContractDefinitionMapper.toContractDefinition(SERVICE_2, SUBJECT_CLIENT, PARTICIPANT_CTX);

        assertThat(result.getId()).isEqualTo("DEV:GOV:5678:SubSys:svc2:DEV:GOV:9999:Consumer-contract-definition");
        assertThat(result.getAssetsSelector().getFirst().getOperandRight()).isEqualTo("DEV:GOV:5678:SubSys:svc2");
    }
}
