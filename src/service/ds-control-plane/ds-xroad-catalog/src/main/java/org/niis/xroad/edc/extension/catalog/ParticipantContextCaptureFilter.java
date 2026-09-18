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
import lombok.RequiredArgsConstructor;

/**
 * Captures the {@code participantContextId} path segment of an incoming DSP protocol request into a
 * request-scoped holder, so the ServerConf-backed catalog stores can serve the record stamped with
 * the addressed context. Every DSP virtual-profile endpoint (catalog, negotiation, transfer) is
 * rooted at {@code /{participantContextId}/{profileId}/...}, per XRDADR-41; JAX-RS accumulates path
 * parameters across the root resource and any sub-resource locators it dispatches through, so this
 * filter sees {@code participantContextId} regardless of which sub-resource ultimately handles the
 * request.
 *
 * <p>The holder is written unconditionally on every request — set to the path segment when present,
 * cleared otherwise — so a pooled request-handling thread can never observe a value left over from a
 * previous request whose path happened to carry no segment.</p>
 */
@RequiredArgsConstructor
class ParticipantContextCaptureFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String PARTICIPANT_CONTEXT_ID_PATH_PARAM = "participantContextId";

    private final ThreadLocalRequestedParticipantContext holder;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var participantContextId = requestContext.getUriInfo().getPathParameters().getFirst(PARTICIPANT_CONTEXT_ID_PATH_PARAM);
        if (participantContextId != null) {
            holder.set(participantContextId);
        } else {
            holder.clear();
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        holder.clear();
    }
}
