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
package org.niis.xroad.cs.test;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.test.api.FeignBackupsApi;
import org.niis.xroad.cs.test.api.FeignCertificationServicesApi;
import org.niis.xroad.cs.test.api.FeignClientsApi;
import org.niis.xroad.cs.test.api.FeignConfigurationPartsApi;
import org.niis.xroad.cs.test.api.FeignConfigurationSourceAnchorApi;
import org.niis.xroad.cs.test.api.FeignConfigurationSourcesApi;
import org.niis.xroad.cs.test.api.FeignGlobalGroupsApi;
import org.niis.xroad.cs.test.api.FeignInitializationApi;
import org.niis.xroad.cs.test.api.FeignIntermediateCasApi;
import org.niis.xroad.cs.test.api.FeignManagementRequestsApi;
import org.niis.xroad.cs.test.api.FeignManagementServicesApi;
import org.niis.xroad.cs.test.api.FeignMemberClassesApi;
import org.niis.xroad.cs.test.api.FeignMembersApi;
import org.niis.xroad.cs.test.api.FeignOcspRespondersApi;
import org.niis.xroad.cs.test.api.FeignOpenapiApi;
import org.niis.xroad.cs.test.api.FeignSecurityServersApi;
import org.niis.xroad.cs.test.api.FeignSubsystemsApi;
import org.niis.xroad.cs.test.api.FeignSystemApi;
import org.niis.xroad.cs.test.api.FeignTimestampingServicesApi;
import org.niis.xroad.cs.test.api.FeignTokensApi;
import org.niis.xroad.cs.test.api.FeignTrustedAnchorsApi;
import org.niis.xroad.test.framework.core.feign.FeignFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.niis.xroad.cs.test.IntTestContainerSetup.CS;
import static org.niis.xroad.cs.test.IntTestContainerSetup.Port.UI;

@Configuration
@RequiredArgsConstructor
public class IntTestConfiguration {
    private final FeignFactory feignFactory;
    private final IntTestContainerSetup testSetup;

    private String getBaseUrl() {
        var container = testSetup.getContainerMapping(CS, UI);

        return "http://" + container.host() + ":" + container.port() + "/api/v1";
    }

    @Bean
    public FeignBackupsApi backupsApi() {
        return feignFactory.createClient(FeignBackupsApi.class, getBaseUrl());
    }

    @Bean
    public FeignCertificationServicesApi certificationServicesApi() {
        return feignFactory.createClient(FeignCertificationServicesApi.class, getBaseUrl());
    }

    @Bean
    public FeignClientsApi clientsApi() {
        return feignFactory.createClient(FeignClientsApi.class, getBaseUrl());
    }

    @Bean
    public FeignConfigurationPartsApi configurationPartsApi() {
        return feignFactory.createClient(FeignConfigurationPartsApi.class, getBaseUrl());
    }

    @Bean
    public FeignConfigurationSourceAnchorApi configurationSourceAnchorApi() {
        return feignFactory.createClient(FeignConfigurationSourceAnchorApi.class, getBaseUrl());
    }

    @Bean
    public FeignConfigurationSourcesApi configurationSourcesApi() {
        return feignFactory.createClient(FeignConfigurationSourcesApi.class, getBaseUrl());
    }

    @Bean
    public FeignGlobalGroupsApi globalGroupsApi() {
        return feignFactory.createClient(FeignGlobalGroupsApi.class, getBaseUrl());
    }

    @Bean
    public FeignInitializationApi initializationApi() {
        return feignFactory.createClient(FeignInitializationApi.class, getBaseUrl());
    }

    @Bean
    public FeignIntermediateCasApi intermediateCasApi() {
        return feignFactory.createClient(FeignIntermediateCasApi.class, getBaseUrl());
    }

    @Bean
    public FeignManagementRequestsApi managementRequestsApi() {
        return feignFactory.createClient(FeignManagementRequestsApi.class, getBaseUrl());
    }

    @Bean
    public FeignManagementServicesApi managementServicesApi() {
        return feignFactory.createClient(FeignManagementServicesApi.class, getBaseUrl());
    }

    @Bean
    public FeignMemberClassesApi memberClassesApi() {
        return feignFactory.createClient(FeignMemberClassesApi.class, getBaseUrl());
    }

    @Bean
    public FeignMembersApi membersApi() {
        return feignFactory.createClient(FeignMembersApi.class, getBaseUrl());
    }

    @Bean
    public FeignOcspRespondersApi ocspRespondersApi() {
        return feignFactory.createClient(FeignOcspRespondersApi.class, getBaseUrl());
    }

    @Bean
    public FeignOpenapiApi openapiApi() {
        return feignFactory.createClient(FeignOpenapiApi.class, getBaseUrl());
    }

    @Bean
    public FeignSecurityServersApi securityServersApi() {
        return feignFactory.createClient(FeignSecurityServersApi.class, getBaseUrl());
    }

    @Bean
    public FeignSubsystemsApi subsystemsApi() {
        return feignFactory.createClient(FeignSubsystemsApi.class, getBaseUrl());
    }

    @Bean
    public FeignSystemApi systemApi() {
        return feignFactory.createClient(FeignSystemApi.class, getBaseUrl());
    }

    @Bean
    public FeignTimestampingServicesApi timestampingServicesApi() {
        return feignFactory.createClient(FeignTimestampingServicesApi.class, getBaseUrl());
    }

    @Bean
    public FeignTokensApi tokensApi() {
        return feignFactory.createClient(FeignTokensApi.class, getBaseUrl());
    }

    @Bean
    public FeignTrustedAnchorsApi trustedAnchorsApi() {
        return feignFactory.createClient(FeignTrustedAnchorsApi.class, getBaseUrl());
    }
}
