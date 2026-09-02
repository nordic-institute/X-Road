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

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DspParticipantContextRequestFilterTest {

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private ContainerResponseContext responseContext;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private MultivaluedMap<String, String> pathParameters;

    private final DspParticipantContextHolder holder = new DspParticipantContextHolder();
    private final DspParticipantContextRequestFilter filter = new DspParticipantContextRequestFilter(holder);

    @Test
    void requestFilterPublishesParticipantContextIdPathParamToHolder() throws Exception {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPathParameters()).thenReturn(pathParameters);
        when(pathParameters.getFirst("participantContextId")).thenReturn("DEV:COM:5678");

        filter.filter(requestContext);

        assertThat(holder.get()).isEqualTo("DEV:COM:5678");
    }

    @Test
    void requestFilterLeavesHolderEmptyWhenRequestHasNoParticipantContextIdSegment() throws Exception {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPathParameters()).thenReturn(pathParameters);
        when(pathParameters.getFirst("participantContextId")).thenReturn(null);

        filter.filter(requestContext);

        assertThat(holder.get()).isNull();
    }

    @Test
    void responseFilterClearsTheHolder() throws Exception {
        holder.set("DEV:COM:5678");

        filter.filter(requestContext, responseContext);

        assertThat(holder.get()).isNull();
    }

    @Test
    void responseFilterClearsTheHolderEvenWhenNothingWasSet() throws Exception {
        filter.filter(requestContext, responseContext);

        assertThat(holder.get()).isNull();
    }
}
