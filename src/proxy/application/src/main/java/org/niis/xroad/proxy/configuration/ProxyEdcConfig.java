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

package org.niis.xroad.proxy.configuration;

import ee.ria.xroad.common.SystemProperties;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Conditional(ProxyEdcConfig.DataspacesEnabledCondition.class)
public class ProxyEdcConfig {

    @Bean
    EdcManagementApiFactory edcManagementApiFactory() {
        return new EdcManagementApiFactory(String.format("%s://%s:%s",
                SystemProperties.isSslEnabled() ? "https" : "http",
                SystemProperties.dataspacesListenAddress(),
                SystemProperties.dataspacesManagementListenPort()));
    }

    @Bean
    EdcControlApiFactory edcControlApiFactory() {
        return new EdcControlApiFactory(String.format("%s://%s:%s",
                SystemProperties.isSslEnabled() ? "https" : "http",
                SystemProperties.dataspacesListenAddress(),
                SystemProperties.dataspacesControlListenPort()));
    }

    @Bean
    DataplaneSelectorControlApi dataplaneSelectorControlApi(EdcControlApiFactory edcControlApiFactory) {
        return edcControlApiFactory.dataplaneSelectorControlApi();
    }

    @Bean
    AssetApi assetApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.assetsApi();
    }

    @Bean
    PolicyDefinitionApiV3 policyDefinitionApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.policyDefinitionApi();
    }

    @Bean
    ContractDefinitionApiV3 contractDefinitionApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.contractDefinitionApi();
    }

    @Bean
    ContractNegotiationApiV3 contractNegotiationApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.contractNegotiationApi();
    }


    @Bean
    CatalogApiV3 feignCatalogApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.catalogApi();
    }

    @Bean
    TransferProcessApiV3 transferProcessApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.transferProcessApi();
    }

    @Bean
    FeignXroadEdrApi xrdEdrApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.xrdEdrApi();
    }

    public static class DataspacesEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return SystemProperties.isDataspacesEnabled();
        }
    }

}
