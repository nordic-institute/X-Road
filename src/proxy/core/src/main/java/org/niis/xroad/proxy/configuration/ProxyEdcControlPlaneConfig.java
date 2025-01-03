package org.niis.xroad.proxy.configuration;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.proxy.conf.KeyConfProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.edc.connector.controlplane.api.management.asset.v3.AssetApi;
import org.eclipse.edc.connector.controlplane.api.management.catalog.v3.CatalogApiV3;
import org.eclipse.edc.connector.controlplane.api.management.contractdefinition.v3.ContractDefinitionApiV3;
import org.eclipse.edc.connector.controlplane.api.management.contractnegotiation.v3.ContractNegotiationApiV3;
import org.eclipse.edc.connector.controlplane.api.management.policy.v3.PolicyDefinitionApiV3;
import org.eclipse.edc.connector.controlplane.api.management.transferprocess.v3.TransferProcessApiV3;
import org.eclipse.edc.connector.dataplane.selector.control.api.DataplaneSelectorControlApi;
import org.niis.xroad.edc.management.client.FeignXroadEdrApi;
import org.niis.xroad.edc.management.client.configuration.EdcControlApiFactory;
import org.niis.xroad.edc.management.client.configuration.EdcManagementApiFactory;

@ApplicationScoped
public class ProxyEdcControlPlaneConfig {

    @Produces
    @ApplicationScoped
    EdcManagementApiFactory edcManagementApiFactory(GlobalConfProvider globalConfProvider,
                                                    KeyConfProvider keyConfProvider) {
        return new EdcManagementApiFactory(
                String.format("%s://%s:%s",
                        SystemProperties.isSslEnabled() ? "https" : "http",
                        SystemProperties.dataspacesListenAddress(),
                        SystemProperties.dataspacesControlPlaneManagementListenPort()),
                globalConfProvider,
                keyConfProvider);
    }

    @Produces
    @ApplicationScoped
    EdcControlApiFactory edcControlApiFactory(GlobalConfProvider globalConfProvider,
                                              KeyConfProvider keyConfProvider) {
        return new EdcControlApiFactory(
                String.format("%s://%s:%s",
                        SystemProperties.isSslEnabled() ? "https" : "http",
                        SystemProperties.dataspacesListenAddress(),
                        SystemProperties.dataspacesControlPlaneControlListenPort()),
                globalConfProvider,
                keyConfProvider);
    }

    @Produces
    @ApplicationScoped
    DataplaneSelectorControlApi dataplaneSelectorControlApi(EdcControlApiFactory factory) {
        return factory.dataplaneSelectorControlApi();
    }

    @Produces
    @ApplicationScoped
    AssetApi assetApi(EdcManagementApiFactory factory) {
        return factory.assetsApi();
    }

    @Produces
    @ApplicationScoped
    PolicyDefinitionApiV3 policyDefinitionApi(EdcManagementApiFactory factory) {
        return factory.policyDefinitionApi();
    }

    @Produces
    @ApplicationScoped
    ContractDefinitionApiV3 contractDefinitionApi(EdcManagementApiFactory factory) {
        return factory.contractDefinitionApi();
    }

    @Produces
    @ApplicationScoped
    ContractNegotiationApiV3 contractNegotiationApi(EdcManagementApiFactory factory) {
        return factory.contractNegotiationApi();
    }

    @Produces
    @ApplicationScoped
    CatalogApiV3 catalogApi(EdcManagementApiFactory factory) {
        return factory.catalogApi();
    }

    @Produces
    @ApplicationScoped
    TransferProcessApiV3 transferProcessApi(EdcManagementApiFactory factory) {
        return factory.transferProcessApi();
    }

    @Produces
    @ApplicationScoped
    FeignXroadEdrApi xrdEdrApi(EdcManagementApiFactory factory) {
        return factory.xrdEdrApi();
    }
}
