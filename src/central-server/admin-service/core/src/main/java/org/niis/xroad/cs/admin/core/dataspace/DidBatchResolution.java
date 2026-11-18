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
package org.niis.xroad.cs.admin.core.dataspace;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.cs.admin.api.domain.DidCheck;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionOutcome;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Resolves DIDs through the resolver and reports, per DID, how the resolution ended. A resolver that fails makes
 * every DID unavailable, and a resolver that does not answer for a DID at its position makes that DID unavailable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DidBatchResolution {

    private final DidResolutionService resolver;

    /**
     * @param dids the DIDs to resolve
     * @return one result per DID, in the order of {@code dids}
     */
    public List<Result> resolve(List<String> dids) {
        List<DidResolution> resolutions;
        try {
            resolutions = resolver.resolve(dids);
        } catch (XrdRuntimeException e) {
            log.warn("DID resolution of {} DIDs failed", dids.size(), e);
            return dids.stream().map(_ -> Result.UNAVAILABLE).toList();
        }
        return IntStream.range(0, dids.size())
                .mapToObj(i -> i < resolutions.size() ? resultOf(dids.get(i), resolutions.get(i)) : Result.UNAVAILABLE)
                .toList();
    }

    private static Result resultOf(String did, DidResolution resolution) {
        if (resolution == null || !did.equals(resolution.did())) {
            return Result.UNAVAILABLE;
        }
        var status = resolution.outcome() == DidResolutionOutcome.RESOLVED ? Status.RESOLVED : Status.NOT_RESOLVED;
        return new Result(status, resolution);
    }

    /**
     * @param status     how the resolution ended
     * @param resolution the resolver's answer for the DID, {@code null} when the resolver was unavailable
     */
    public record Result(Status status, @Nullable DidResolution resolution) {

        private static final Result UNAVAILABLE = new Result(Status.RESOLVER_UNAVAILABLE, null);

        /**
         * @return the DID check for an acceptable DID that was resolved to this result
         */
        public DidCheck toDidCheck() {
            return switch (status) {
                case RESOLVED -> DidCheck.RESOLVED;
                case NOT_RESOLVED -> DidCheck.notResolved(resolution.reason());
                case RESOLVER_UNAVAILABLE -> DidCheck.RESOLVER_UNAVAILABLE;
            };
        }
    }

    public enum Status {
        RESOLVED,
        NOT_RESOLVED,
        RESOLVER_UNAVAILABLE
    }
}
