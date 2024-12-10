package org.niis.xroad.edc.extension.policy.dataplane.transform;

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

public class JsonObjectToContractAgreementTransformer extends AbstractJsonLdTransformer<JsonObject, ContractAgreement> {

    public JsonObjectToContractAgreementTransformer() {
        super(JsonObject.class, ContractAgreement.class);
    }

    @Override
    public @Nullable ContractAgreement transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var policyObject = returnMandatoryJsonObject(object.get(CONTRACT_AGREEMENT_POLICY), context, CONTRACT_AGREEMENT_POLICY);
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
                .consumerId(transformString(object.get(CONTRACT_AGREEMENT_CONSUMER_ID), context))
                .providerId(transformString(object.get(CONTRACT_AGREEMENT_PROVIDER_ID), context))
                .assetId(transformString(object.get(CONTRACT_AGREEMENT_ASSET_ID), context))
                .contractSigningDate(transformInt(object.get(CONTRACT_AGREEMENT_SIGNING_DATE), context));
        return builder.build();
    }
}
