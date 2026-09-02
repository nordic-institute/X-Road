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
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Publishes the {@code participantContextId} path segment of every DSP protocol HTTP request to
 * {@link DspParticipantContextHolder}, for the duration of that request.
 *
 * <p>EDC's virtual multi-participant DSP controllers (negotiation, transfer-process, catalog) all
 * mount their resources under {@code /{participantContextId}/{profileId}/...}; once JAX-RS resolves
 * the matching resource method, {@link ContainerRequestContext#getUriInfo()}'s path parameters
 * carry that segment regardless of whether the resource method itself declares a
 * {@code @PathParam} for it. A request that doesn't match that shape (management API, health
 * checks, a DSP profile that doesn't scope by participant) simply leaves the holder empty.
 *
 * <p>Registered on {@code ApiContext.PROTOCOL} by {@link XRoadServerConfCatalogExtension}, so it
 * runs as an ordinary JAX-RS provider in the same web context as the DSP resources — it neither
 * replaces nor depends on internals of any EDC-provided controller or filter.
 */
@Slf4j
@Provider
@RequiredArgsConstructor
class DspParticipantContextRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String PATH_PARAM_PARTICIPANT_CONTEXT_ID = "participantContextId";

    private final DspParticipantContextHolder holder;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        var participantContextId = requestContext.getUriInfo().getPathParameters().getFirst(PATH_PARAM_PARTICIPANT_CONTEXT_ID);
        if (participantContextId != null) {
            log.trace("DSP request path carries participantContextId={}", participantContextId);
            holder.set(participantContextId);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        holder.clear();
    }
}
