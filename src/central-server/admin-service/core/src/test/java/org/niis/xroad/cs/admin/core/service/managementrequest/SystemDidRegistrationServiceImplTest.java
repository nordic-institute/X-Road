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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.PublishedValueRejection;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.PublishedValueStore;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;

class SystemDidRegistrationServiceImplTest {
    private static final int SERVER_ROW_ID = 7;
    private static final String SYSTEM_DID = "did:web:ss.example.org:v1:system";
    private static final String DSP_BASE_URL = "https://ss.example.org:7183/dsp";
    private static final byte[] CERTIFICATE = {1, 2, 3};

    private final SecurityServerId securityServerId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");
    private final SystemDidRegistrationRequest request =
            new SystemDidRegistrationRequest(SECURITY_SERVER, securityServerId, SYSTEM_DID, DSP_BASE_URL, CERTIFICATE);

    private final List<String> calls = new ArrayList<>();
    private SystemPairCheck storeVerdict = SystemPairCheck.NOT_EQUAL;
    private DidResolution resolution = DidResolution.resolved(SYSTEM_DID);

    private final AuthCertServerCheck authCertServerCheck = mock(AuthCertServerCheck.class);
    private final PublishedValueStore store = mock(PublishedValueStore.class);
    private final ManagementRequestService managementRequestService = mock(ManagementRequestService.class);
    private final DidResolutionService resolver = dids -> {
        calls.add("resolve" + dids);
        return List.of(resolution);
    };

    private SystemDidRegistrationServiceImpl service() {
        when(authCertServerCheck.requireServerRowId(any(), any())).thenAnswer(inv -> {
            calls.add("certificate");
            return SERVER_ROW_ID;
        });
        when(store.checkSystemPair(SERVER_ROW_ID, SYSTEM_DID, DSP_BASE_URL)).thenAnswer(inv -> {
            calls.add("store");
            return storeVerdict;
        });
        when(managementRequestService.add(request)).thenAnswer(inv -> {
            calls.add("add");
            return request;
        });
        return new SystemDidRegistrationServiceImpl(authCertServerCheck, store, resolver, managementRequestService);
    }

    @Test
    void checksCertificateThenStoreThenResolvesOneDidThenAdds() {
        var result = service().add(request);

        assertThat(calls).containsExactly("certificate", "store", "resolve[" + SYSTEM_DID + "]", "add");
        assertThat(result).isSameAs(request);
    }

    @Test
    void equalPairIsAddedWithoutResolving() {
        storeVerdict = SystemPairCheck.EQUAL;

        service().add(request);

        assertThat(calls).containsExactly("certificate", "store", "add");
    }

    @Test
    void rejectedPairIsNotResolvedOrAdded() {
        storeVerdict = SystemPairCheck.rejected(PublishedValueRejection.HOST_DIFFERS);

        var service = service();
        assertThatThrownBy(() -> service.add(request)).isInstanceOf(BadRequestException.class);

        assertThat(calls).containsExactly("certificate", "store");
    }

    @Test
    void unresolvedDidIsNotAdded() {
        resolution = DidResolution.notResolved(SYSTEM_DID, DidResolutionReason.values()[0]);

        var service = service();
        assertThatThrownBy(() -> service.add(request)).isInstanceOf(BadRequestException.class);

        assertThat(calls).containsExactly("certificate", "store", "resolve[" + SYSTEM_DID + "]");
    }
}
