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
package org.niis.xroad.ds.issuance.identity;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.ds.issuance.identity.StatusListCredentialFixture.statusListCredential;

class XRoadStatusListCredentialControllerTest {

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(20);
    private static final String CALLBACK_ADDRESS = "https://callback.example:6187/statuslist";
    private static final String BOUND_BASE_URL = "https://issuer.example/statuslist";

    private final Monitor monitor = mock();
    private final CredentialStore store = mock();

    @Test
    void servesAListCreatedUnderABoundBaseThatDiffersFromTheCallback() {
        var bound = new BoundIssuerIdentity("DID-SERVE", "issuer.example", 7183, "https://issuer.example/issuance", BOUND_BASE_URL);
        var holder = new BoundIssuerIdentityHolder(() -> () -> Optional.of(bound), monitor, CHECK_INTERVAL, CHECK_INTERVAL);
        holder.start();
        var credentialId = BOUND_BASE_URL + "/credential-1";
        when(store.query(any())).thenReturn(StoreResult.success(List.of(statusListCredential(credentialId))));
        var controller = new XRoadStatusListCredentialController(store, monitor, () -> JsonMapper.builder().build(), holder);

        var response = controller.resolveStatusListCredential(MediaType.WILDCARD, requestContext(CALLBACK_ADDRESS + "/credential-1"));

        assertThat(response.getStatus()).isEqualTo(200);
        var query = capturedQuery();
        assertThat(query.getFilterExpression())
                .anySatisfy(criterion -> assertThat(criterion.getOperandRight()).isEqualTo(credentialId));
    }

    @Test
    void returnsServiceUnavailableWhileUnbound() {
        var holder = new BoundIssuerIdentityHolder(() -> Optional::empty, monitor, CHECK_INTERVAL, CHECK_INTERVAL);
        var controller = new XRoadStatusListCredentialController(store, monitor, () -> JsonMapper.builder().build(), holder);

        var response = controller.resolveStatusListCredential(MediaType.WILDCARD, requestContext(CALLBACK_ADDRESS + "/credential-1"));

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(store);
    }

    private QuerySpec capturedQuery() {
        var captor = forClass(QuerySpec.class);
        verify(store).query(captor.capture());
        return captor.getValue();
    }

    private static ContainerRequestContext requestContext(String absoluteUrl) {
        var uriInfo = mock(UriInfo.class);
        when(uriInfo.getAbsolutePath()).thenReturn(URI.create(absoluteUrl));
        var context = mock(ContainerRequestContext.class);
        when(context.getUriInfo()).thenReturn(uriInfo);
        return context;
    }
}
