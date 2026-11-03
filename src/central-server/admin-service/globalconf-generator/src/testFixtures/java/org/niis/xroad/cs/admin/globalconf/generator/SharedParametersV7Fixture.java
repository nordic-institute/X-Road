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

package org.niis.xroad.cs.admin.globalconf.generator;

import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.service.CertificationServicesService;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.DataspaceMemberDidService;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificationAuthoritiesService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupService;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TimestampingServicesService;
import org.niis.xroad.globalconf.model.SharedParameters;

import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the version 7 shared parameters loader and generator, which are package private, over the given services.
 * Every other source of the shared parameters is empty.
 */
public final class SharedParametersV7Fixture {

    private static final String INSTANCE = "DEV";
    private static final int OCSP_FRESHNESS_SECONDS = 3600;

    private SharedParametersV7Fixture() {
    }

    /**
     * @param parameters the loaded shared parameters, member DIDs included
     * @param xml        the version 7 shared parameters document
     */
    public record Emitted(SharedParameters parameters, String xml) {
    }

    public static Emitted emit(ClientService clients, SecurityServerService servers, DataspaceMemberDidService memberDids) {
        var systemParameters = mock(SystemParameterService.class);
        when(systemParameters.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(systemParameters.getOcspFreshnessSeconds()).thenReturn(OCSP_FRESHNESS_SECONDS);
        var configuration = mock(ConfigurationService.class);
        when(configuration.getNodeAddressesWithOrderedConfigurationSigningKeys()).thenReturn(Map.of("cs", List.of(
                signingKey(ConfigurationSourceType.INTERNAL), signingKey(ConfigurationSourceType.EXTERNAL))));
        var loader = new SharedParametersLoader(systemParameters, mock(CertificationServicesService.class),
                mock(TimestampingServicesService.class), mock(DsTlsCertificationAuthoritiesService.class),
                mock(DataspaceIssuerDidService.class), memberDids, clients, servers, mock(GlobalGroupService.class),
                mock(GlobalGroupMemberService.class), mock(MemberClassService.class), configuration);
        return new Emitted(loader.loadWithMemberDids(), new SharedParametersV7Generator(loader).generate());
    }

    private static ConfigurationSigningKey signingKey(ConfigurationSourceType sourceType) {
        return new ConfigurationSigningKey()
                .setKeyIdentifier("key-" + sourceType)
                .setCert(("cert-" + sourceType).getBytes(UTF_8))
                .setSourceType(sourceType)
                .setActiveSourceSigningKey(true);
    }
}
