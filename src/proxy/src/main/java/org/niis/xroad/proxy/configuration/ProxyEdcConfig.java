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

import org.eclipse.edc.connector.api.management.asset.v3.AssetApi;
import org.eclipse.edc.connector.api.management.contractdefinition.ContractDefinitionApi;
import org.eclipse.edc.connector.api.management.contractnegotiation.ContractNegotiationApi;
import org.eclipse.edc.connector.api.management.policy.PolicyDefinitionApi;
import org.eclipse.edc.connector.api.management.transferprocess.TransferProcessApi;
import org.eclipse.edc.connector.dataplane.selector.api.v2.DataplaneSelectorApi;
import org.niis.xroad.edc.management.client.FeignCatalogApi;
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
                SystemProperties.dataspacesManagementListenAddress(),
                SystemProperties.dataspacesManagementListenPort()));
    }

    @Bean
    AssetApi assetApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.assetsApi();
    }

    @Bean
    PolicyDefinitionApi policyDefinitionApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.policyDefinitionApi();
    }

    @Bean
    DataplaneSelectorApi dataplaneSelectorApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.dataplaneSelectorApi();
    }

    @Bean
    ContractDefinitionApi contractDefinitionApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.contractDefinitionApi();
    }

    @Bean
    ContractNegotiationApi contractNegotiationApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.contractNegotiationApi();
    }


    @Bean
    FeignCatalogApi feignCatalogApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.catalogApi();
    }

    @Bean
    TransferProcessApi transferProcessApi(EdcManagementApiFactory edcManagementApiFactory) {
        return edcManagementApiFactory.transferProcessApi();
    }

    public static class DataspacesEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return SystemProperties.isDataspacesEnabled();
        }
    }

}
