/*
 * The MIT License
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
