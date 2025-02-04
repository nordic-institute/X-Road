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
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_ASSET_ID;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_CONSUMER_ID;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_POLICY;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_PROVIDER_ID;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_SIGNING_DATE;
import static org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement.CONTRACT_AGREEMENT_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNEE_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNER_ATTRIBUTE;

public class JsonObjectToContractAgreementTransformer extends AbstractJsonLdTransformer<JsonObject, ContractAgreement> {

    public JsonObjectToContractAgreementTransformer() {
        super(JsonObject.class, ContractAgreement.class);
    }

    @Override
    public @Nullable ContractAgreement transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        String consumerId = transformString(object.get(CONTRACT_AGREEMENT_CONSUMER_ID), context);
        String providerId = transformString(object.get(CONTRACT_AGREEMENT_PROVIDER_ID), context);

        var policyObject = returnMandatoryJsonObject(object.get(CONTRACT_AGREEMENT_POLICY), context, CONTRACT_AGREEMENT_POLICY);
        // assignee and assigner are mandatory when transforming policy to POJO
        policyObject = Json.createObjectBuilder(policyObject)
                .add(ODRL_ASSIGNEE_ATTRIBUTE, consumerId)
                .add(ODRL_ASSIGNER_ATTRIBUTE, providerId)
                .build();
        var policy = context.transform(policyObject, Policy.class);
        if (policy == null) {
            context.problem()
                    .invalidProperty()
                    .type(CONTRACT_AGREEMENT_TYPE)
                    .property(CONTRACT_AGREEMENT_POLICY)
                    .report();
            return null;
        }

        var builder = ContractAgreement.Builder.newInstance();
        var agreementId = nodeId(object);
        if (agreementId == null) {
            context.problem()
                    .missingProperty()
                    .type(CONTRACT_AGREEMENT_TYPE)
                    .property(ID)
                    .report();
            return null;
        }

        builder.id(agreementId)
                .policy(policy)
                .consumerId(consumerId)
                .providerId(providerId)
                .assetId(transformString(object.get(CONTRACT_AGREEMENT_ASSET_ID), context))
                .contractSigningDate(transformInt(object.get(CONTRACT_AGREEMENT_SIGNING_DATE), context));
        return builder.build();
    }
}
