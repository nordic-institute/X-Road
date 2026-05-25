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
package org.niis.xroad.edc.extension.assetaccess.grpc;

import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

/**
 * Maps EDC failure shapes to X-Road {@link org.niis.xroad.common.core.exception.ErrorCode}s.
 */
final class DspFailureClassifier {

    static final String MSG_PREFIX_FETCH_CATALOG = "Failed to fetch catalog: ";
    static final String MSG_PREFIX_PARSE_CATALOG = "Error parsing catalog response";
    static final String MSG_PREFIX_PARSE_CATALOG_ALT = "Failed to parse catalog: ";
    static final String MSG_PREFIX_NO_DATASET = "No dataset found for asset ID: ";
    static final String MSG_PREFIX_NO_OFFERS = "No offers found for asset ID: ";
    static final String MSG_PREFIX_NO_PULL_DIST = "No PULL distribution found for asset ID: ";
    static final String MSG_PREFIX_PARTICIPANT_CONTEXT = "Failed to resolve participant context: ";
    static final String MSG_PREFIX_TIMED_OUT = "Asset access acquisition timed out";

    private DspFailureClassifier() {
    }

    /**
     * Classifies a DSP failure into an {@link XrdRuntimeException} with an appropriate error code.
     * Walks the cause chain to find the most specific message for classification.
     *
     * @param cause the throwable to classify
     * @return an {@link XrdRuntimeException} with the mapped error code
     */
    static XrdRuntimeException classify(Throwable cause) {
        return classifyChain(cause, cause);
    }

    private static XrdRuntimeException classifyChain(Throwable root, Throwable current) {
        if (current instanceof XrdRuntimeException xre) {
            return xre;
        }
        var classified = tryClassifyByMessage(current);
        if (classified != null) {
            return classified;
        }
        if (current.getCause() != null) {
            return classifyChain(root, current.getCause());
        }
        return classifyByMessage(root);
    }

    private static XrdRuntimeException tryClassifyByMessage(Throwable cause) {
        var msg = cause.getMessage();
        if (msg == null) {
            return null;
        }
        if (msg.startsWith(MSG_PREFIX_TIMED_OUT)) {
            return XrdRuntimeException.systemException(NETWORK_ERROR, cause, msg);
        }
        if (msg.startsWith(MSG_PREFIX_FETCH_CATALOG)) {
            return XrdRuntimeException.systemException(NETWORK_ERROR, cause, msg);
        }
        if (msg.startsWith(MSG_PREFIX_PARSE_CATALOG) || msg.startsWith(MSG_PREFIX_PARSE_CATALOG_ALT)) {
            return XrdRuntimeException.systemException(NETWORK_ERROR, cause, msg);
        }
        if (msg.startsWith(MSG_PREFIX_NO_DATASET) || msg.startsWith(MSG_PREFIX_NO_OFFERS)) {
            return XrdRuntimeException.systemException(UNKNOWN_MEMBER, cause, msg);
        }
        if (msg.startsWith(MSG_PREFIX_NO_PULL_DIST)) {
            return XrdRuntimeException.systemException(SERVICE_FAILED, cause, msg);
        }
        if (msg.startsWith(MSG_PREFIX_PARTICIPANT_CONTEXT)) {
            return XrdRuntimeException.systemException(INTERNAL_ERROR, cause, msg);
        }
        return null;
    }

    private static XrdRuntimeException classifyByMessage(Throwable cause) {
        var classified = tryClassifyByMessage(cause);
        if (classified != null) {
            return classified;
        }
        var msg = cause.getMessage();
        if (msg == null) {
            return XrdRuntimeException.systemException(SERVICE_FAILED, cause, "Unknown DSP failure");
        }
        return XrdRuntimeException.systemException(SERVICE_FAILED, cause, msg);
    }
}
