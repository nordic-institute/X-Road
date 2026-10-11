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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import io.grpc.Status;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A gRPC status for a message that exceeds the receiver's maximum inbound message size.
 *
 * @param limit the maximum inbound message size in bytes
 * @param size the size in bytes of the rejected message
 */
public record InboundMessageSizeRejection(long limit, long size) {
    /**
     * Part of the status description grpc-java uses when a message exceeds the receiver's maximum inbound message size.
     */
    private static final String DESCRIPTION_TEXT = "gRPC message exceeds maximum size";
    private static final Pattern DESCRIPTION = Pattern.compile(Pattern.quote(DESCRIPTION_TEXT) + " (\\d+): (\\d+)");

    /**
     * Tells whether the status rejects a message for exceeding the maximum inbound message size.
     * @param status the status of a failed call
     * @return true for such a rejection
     */
    private static boolean isRejection(Status status) {
        return status.getCode() == Status.Code.RESOURCE_EXHAUSTED
                && status.getDescription() != null
                && status.getDescription().contains(DESCRIPTION_TEXT);
    }

    /**
     * Reads the limit and the message size from a rejection status.
     * @param status the status of a failed call
     * @return the limit and the size, or empty when the status is not such a rejection or does not state both
     */
    public static Optional<InboundMessageSizeRejection> from(Status status) {
        if (!isRejection(status)) {
            return Optional.empty();
        }
        var matcher = DESCRIPTION.matcher(status.getDescription());
        return matcher.find()
                ? Optional.of(new InboundMessageSizeRejection(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))))
                : Optional.empty();
    }
}
