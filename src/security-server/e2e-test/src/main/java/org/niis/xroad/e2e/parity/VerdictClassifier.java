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

package org.niis.xroad.e2e.parity;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a REST or SOAP response from the consumer Security Server's proxy into a {@link Verdict}.
 *
 * <p>For REST, {@link Verdict#ALLOWED} needs a 2xx status and the {@code mock-response: true} header the backend stub
 * adds, so a 2xx that did not reach the backend is an error. A provider refusal arrives as HTTP 500 with an
 * {@code X-Road-Error} header holding {@code server.serverproxy.} followed by the error code. Every other shape
 * is {@link Verdict#ERROR}.
 */
public final class VerdictClassifier {

    static final String ERROR_HEADER = "x-road-error";
    static final String BACKEND_MARKER_HEADER = "mock-response";
    static final String SOAP_BACKEND_MARKER = "acl-parity-backend";

    private static final String FAULT_PREFIX = "server.serverproxy.";
    private static final String CLIENT_FAULT_PREFIX = "server.clientproxy.";
    private static final int HTTP_OK = 200;
    private static final int HTTP_LAST_SUCCESS = 299;
    private static final int HTTP_SERVER_ERROR = 500;

    private static final Pattern FAULT_CODE =
            Pattern.compile("<(?:[\\w.-]+:)?faultcode[^>]*>\\s*([^<]*?)\\s*</(?:[\\w.-]+:)?faultcode>");

    private static final Map<String, Verdict> FAULTS = Map.of(
            FAULT_PREFIX + "access_denied", Verdict.ACCESS_DENIED,
            FAULT_PREFIX + "unknown_service", Verdict.UNKNOWN_SERVICE,
            FAULT_PREFIX + "service_disabled", Verdict.SERVICE_DISABLED,
            FAULT_PREFIX + "service_type", Verdict.INVALID_SERVICE_TYPE,
            FAULT_PREFIX + "unknown_member", Verdict.UNKNOWN_MEMBER,
            FAULT_PREFIX + "invalid_security_server", Verdict.INVALID_SECURITY_SERVER,
            CLIENT_FAULT_PREFIX + "invalid_security_server", Verdict.INVALID_SECURITY_SERVER);

    private VerdictClassifier() {
    }

    public static Verdict classify(Reply reply) {
        if (reply == null) {
            return Verdict.ERROR;
        }
        if (reply.status() >= HTTP_OK && reply.status() <= HTTP_LAST_SUCCESS) {
            return "true".equalsIgnoreCase(reply.header(BACKEND_MARKER_HEADER)) ? Verdict.ALLOWED : Verdict.ERROR;
        }
        if (reply.status() == HTTP_SERVER_ERROR) {
            var fault = reply.header(ERROR_HEADER);
            return fault == null ? Verdict.ERROR : FAULTS.getOrDefault(fault.trim(), Verdict.ERROR);
        }
        return Verdict.ERROR;
    }

    /**
     * Classifies a SOAP answer. A fault code in the body decides the verdict whatever the HTTP status; without a
     * fault, only a 2xx answer carrying the backend's body marker is {@link Verdict#ALLOWED}.
     */
    public static Verdict classifySoap(Reply reply) {
        if (reply == null) {
            return Verdict.ERROR;
        }
        var fault = FAULT_CODE.matcher(reply.body());
        if (fault.find()) {
            return FAULTS.getOrDefault(fault.group(1), Verdict.ERROR);
        }
        var success = reply.status() >= HTTP_OK && reply.status() <= HTTP_LAST_SUCCESS;
        return success && reply.body().contains(SOAP_BACKEND_MARKER) ? Verdict.ALLOWED : Verdict.ERROR;
    }

    /** The fault code of a SOAP answer, or its HTTP status when the body holds no fault code. */
    public static String describeSoap(Reply reply) {
        var fault = FAULT_CODE.matcher(reply.body());
        return fault.find() ? "fault " + fault.group(1) : "status " + reply.status();
    }
}
