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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.AccessRight;
import org.niis.xroad.serverconf.model.Endpoint;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadServerConfCatalogExtensionTest {

    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create("DEV", "GOV", "1111", "ss0");

    @Mock
    private ServerConfProvider serverConfProvider;

    @Mock
    private GlobalConfProvider globalConfProvider;

    @Mock
    private ParticipantContextService participantContextService;

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private WebService webService;

    private XRoadServerConfCatalogExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        extension = new XRoadServerConfCatalogExtension();

        setField(extension, "serverConfProvider", serverConfProvider);
        setField(extension, "globalConfProvider", globalConfProvider);
        setField(extension, "participantContextService", participantContextService);
        setField(extension, "webService", webService);

        when(serverConfProvider.getIdentifier()).thenReturn(SS_ID);
        when(context.getSetting(anyString(), anyString())).thenAnswer(inv -> inv.getArgument(1));
        when(context.getSetting(anyString(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(context.getSetting(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        when(context.getSetting(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        extension.initialize(context);
    }

    @Test
    void assetIndexReturnsCorrectInstance() {
        assertThat(extension.assetIndex()).isInstanceOf(AssetIndexServerConfStore.class);
    }

    @Test
    void policyDefinitionStoreReturnsCorrectInstance() {
        assertThat(extension.policyDefinitionStore()).isInstanceOf(PolicyDefinitionServerConfStore.class);
    }

    @Test
    void contractDefinitionStoreReturnsCorrectInstance() {
        assertThat(extension.contractDefinitionStore()).isInstanceOf(ContractDefinitionServerConfStore.class);
    }

    @Test
    void initializeRegistersParticipantContextFilterOnProtocolApiContext() {
        verify(webService).registerResource(eq(ApiContext.PROTOCOL), any(DspParticipantContextRequestFilter.class));
    }

    @Test
    void catalogCacheInvalidatorReturnsWorkingInstance() {
        assertThat(extension.catalogCacheInvalidator()).isInstanceOf(CatalogCacheInvalidator.class);
    }

    @Test
    void catalogCacheInvalidatorInvalidatesAssetPolicyAndContractStoreCaches() {
        when(participantContextService.search(any())).thenReturn(ServiceResult.success(List.of()));
        var member = ClientId.Conf.create("DEV", "GOV", "1111", "SubsystemA");
        var service = ServiceId.Conf.create("DEV", "GOV", "1111", "SubsystemA", "getRecords", "v1");
        when(serverConfProvider.getMembers()).thenReturn(List.of(member));
        when(serverConfProvider.getAllServices(member)).thenReturn(List.of(service));
        when(serverConfProvider.getDisabledNotice(service)).thenReturn(null);
        when(serverConfProvider.getServiceAccessRights(service)).thenReturn(nonEmptyAcl());

        var assetIndex = extension.assetIndex();
        var policyStore = extension.policyDefinitionStore();
        var contractStore = extension.contractDefinitionStore();

        assetIndex.queryAssets(QuerySpec.max()).count();
        policyStore.findAll(QuerySpec.max()).count();
        contractStore.findAll(QuerySpec.max()).count();
        verify(serverConfProvider, times(3)).getMembers();

        extension.catalogCacheInvalidator().invalidateStoreCaches();

        assetIndex.queryAssets(QuerySpec.max()).count();
        policyStore.findAll(QuerySpec.max()).count();
        contractStore.findAll(QuerySpec.max()).count();
        verify(serverConfProvider, times(6)).getMembers();
    }

    private static List<AccessRight> nonEmptyAcl() {
        var ar = new AccessRight();
        ar.setSubjectId(ClientId.Conf.create("DEV", "GOV", "9999", "Consumer"));
        ar.setEndpoint(new Endpoint("svc", "GET", "/", false));
        ar.setRightsGiven(new Date());
        return List.of(ar);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
