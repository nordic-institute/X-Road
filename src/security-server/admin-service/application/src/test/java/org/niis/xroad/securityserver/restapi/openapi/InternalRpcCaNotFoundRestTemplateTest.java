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
package org.niis.xroad.securityserver.restapi.openapi;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_RPC_CA_NOT_AVAILABLE;

/**
 * Internal RPC CA endpoints answer 404 with a JSON error body through the whole Spring MVC stack.
 */
public class InternalRpcCaNotFoundRestTemplateTest extends AbstractApiControllerTestContext {

    private static final MediaType PEM_FILE = MediaType.parseMediaType("application/x-pem-file");

    @Autowired
    WebTestClient webTestClient;

    private WebTestClient client;

    @Before
    public void setup() {
        client = TestUtils.addApiKeyAuthorizationHeader(webTestClient);
        when(internalRpcCaService.getInternalRpcCa()).thenThrow(notAvailable());
        when(internalRpcCaService.downloadInternalRpcCa()).thenThrow(notAvailable());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_INTERNAL_RPC_CA"})
    public void detailsAnswerNotFoundWithJsonErrorInfo() {
        client.get().uri("/api/v1/system/internal-rpc-ca")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error.code").isEqualTo(INTERNAL_RPC_CA_NOT_AVAILABLE.code());
    }

    @Test
    @WithMockUser(authorities = {"DOWNLOAD_INTERNAL_RPC_CA"})
    public void downloadAnswersNotFoundWithJsonErrorInfoWhenThePemTypeIsAccepted() {
        client.get().uri("/api/v1/system/internal-rpc-ca/download")
                .accept(PEM_FILE, MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error.code").isEqualTo(INTERNAL_RPC_CA_NOT_AVAILABLE.code());
    }

    private static NotFoundException notAvailable() {
        return new NotFoundException(INTERNAL_RPC_CA_NOT_AVAILABLE.build());
    }
}
