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

package org.niis.xroad.e2e.parity.acl;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a REST or SOAP response from the consumer Security Server's proxy into a {@link Verdict}.
 *
 * <p>For REST, {@link Verdict#ALLOWED} needs a 2xx status and the {@code mock-response: true} header the backend stub
 * adds, so a 2xx that did not reach the backend is an error. A provider refusal arrives as HTTP 500 with an
 * {@code X-Road-Error} header holding {@code server.serverproxy.} followed by the error code. HTTP 400 without an
 * {@code X-Road-Error} header is the consumer proxy's HTTP server refusing the request URI before any X-Road
 * handler ran, which is {@link Verdict#CONSUMER_REJECTED}. Every other shape is {@link Verdict#ERROR}.
 */
public final class VerdictClassifier {

    static final String ERROR_HEADER = "x-road-error";
    static final String BACKEND_MARKER_HEADER = "mock-response";
    static final String SOAP_BACKEND_MARKER = "acl-parity-backend";

    private static final String FAULT_PREFIX = "server.serverproxy.";
    private static final String CLIENT_FAULT_PREFIX = "server.clientproxy.";
    private static final int HTTP_OK = 200;
    private static final int HTTP_LAST_SUCCESS = 299;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_SERVER_ERROR = 500;

    private static final int DESCRIBED_BODY_LENGTH = 300;
    private static final Pattern ACCESS_DENIED_CODE = Pattern.compile("server\\.serverproxy\\.access_denied");
    private static final Pattern FAULT_CODE =
            Pattern.compile("<(?:[\\w.-]+:)?faultcode[^>]*>\\s*([^<]*?)\\s*</(?:[\\w.-]+:)?faultcode>");

    private static final Map<String, Verdict> FAULTS = Map.of(
            FAULT_PREFIX + "access_denied", Verdict.ACCESS_DENIED,
            FAULT_PREFIX + "unknown_service", Verdict.UNKNOWN_SERVICE,
            FAULT_PREFIX + "service_disabled", Verdict.SERVICE_DISABLED,
            FAULT_PREFIX + "service_type", Verdict.INVALID_SERVICE_TYPE,
            FAULT_PREFIX + "unknown_member", Verdict.UNKNOWN_MEMBER,
            FAULT_PREFIX + "invalid_security_server", Verdict.INVALID_SECURITY_SERVER,
            FAULT_PREFIX + "internal_error", Verdict.INTERNAL_ERROR,
            CLIENT_FAULT_PREFIX + "invalid_security_server", Verdict.INVALID_SECURITY_SERVER,
            CLIENT_FAULT_PREFIX + "unknown_member", Verdict.UNKNOWN_MEMBER);

    private VerdictClassifier() {
    }

    public static Verdict classify(Reply reply) {
        if (reply == null) {
            return Verdict.ERROR;
        }
        if (reply.status() >= HTTP_OK && reply.status() <= HTTP_LAST_SUCCESS) {
            return "true".equalsIgnoreCase(reply.header(BACKEND_MARKER_HEADER)) ? Verdict.ALLOWED : Verdict.ERROR;
        }
        if (reply.status() == HTTP_BAD_REQUEST && reply.header(ERROR_HEADER) == null) {
            return Verdict.CONSUMER_REJECTED;
        }
        if (reply.status() == HTTP_SERVER_ERROR) {
            var fault = reply.header(ERROR_HEADER);
            return fault == null ? Verdict.ERROR : FAULTS.getOrDefault(fault.trim(), Verdict.ERROR);
        }
        return Verdict.ERROR;
    }

    /**
     * Classifies the answer of the admin API to a client registration, which the Security Server forwards to the
     * management service. A 2xx answer is {@link Verdict#ALLOWED}; a body that carries the fault code
     * {@code server.serverproxy.access_denied} is {@link Verdict#ACCESS_DENIED}; every other shape is
     * {@link Verdict#ERROR}.
     */
    public static Verdict classifyManagement(Reply reply) {
        if (reply == null) {
            return Verdict.ERROR;
        }
        if (reply.status() >= HTTP_OK && reply.status() <= HTTP_LAST_SUCCESS) {
            return Verdict.ALLOWED;
        }
        return ACCESS_DENIED_CODE.matcher(reply.body()).find() ? Verdict.ACCESS_DENIED : Verdict.ERROR;
    }

    /** The status and the start of the body of an admin API answer, for failure messages. */
    public static String describeManagement(Reply reply) {
        var body = reply.body();
        return "status %d; body %s".formatted(reply.status(),
                body.length() > DESCRIBED_BODY_LENGTH ? body.substring(0, DESCRIBED_BODY_LENGTH) : body);
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

    /**
     * Classifies a REST or SOAP metaservice answer. A fault, in the body or in the {@code X-Road-Error} header,
     * decides the verdict; without one, a 2xx answer is {@link Verdict#ALLOWED} only when it carries every required
     * text and none of the forbidden ones, so a list that has not yet caught up with a change reads as
     * {@link Verdict#ERROR} and is polled again. Texts match as whole tokens, see {@link AclScenario.Expectation}.
     */
    public static Verdict classifyMetaservice(Reply reply, List<String> required, List<String> forbidden) {
        if (reply == null) {
            return Verdict.ERROR;
        }
        var fault = FAULT_CODE.matcher(reply.body());
        if (fault.find()) {
            return FAULTS.getOrDefault(fault.group(1), Verdict.ERROR);
        }
        if (reply.header(ERROR_HEADER) != null) {
            return FAULTS.getOrDefault(reply.header(ERROR_HEADER).trim(), Verdict.ERROR);
        }
        var success = reply.status() >= HTTP_OK && reply.status() <= HTTP_LAST_SUCCESS;
        return success && missing(reply, required).isEmpty() && present(reply, forbidden).isEmpty()
                ? Verdict.ALLOWED : Verdict.ERROR;
    }

    /** What a metaservice answer says, for failure messages: its fault or status, and the texts it got wrong. */
    public static String describeMetaservice(Reply reply, List<String> required, List<String> forbidden) {
        return "%s; missing %s; unexpected %s".formatted(faultOrStatus(reply), missing(reply, required),
                present(reply, forbidden));
    }

    private static String faultOrStatus(Reply reply) {
        var fault = FAULT_CODE.matcher(reply.body());
        if (fault.find()) {
            return "fault " + fault.group(1);
        }
        var header = reply.header(ERROR_HEADER);
        return header == null ? "status " + reply.status() : "x-road-error " + header;
    }

    private static List<String> missing(Reply reply, List<String> required) {
        return required.stream().filter(text -> !mentions(reply.body(), text)).toList();
    }

    private static List<String> present(Reply reply, List<String> forbidden) {
        return forbidden.stream().filter(text -> mentions(reply.body(), text)).toList();
    }

    private static boolean mentions(String body, String text) {
        return Pattern.compile("(?<![\\w.-])" + Pattern.quote(text) + "(?![\\w.-])").matcher(body).find();
    }

    /**
     * What a REST answer says, for failure messages: its status and fault header, and for a disabled-service fault
     * also its body, which carries the operator's notice.
     */
    public static String describe(Reply reply) {
        return classify(reply) == Verdict.SERVICE_DISABLED ? reply + "; body " + reply.body() : reply.toString();
    }

    /** The fault code of a SOAP answer, or its HTTP status when the body holds no fault code. */
    public static String describeSoap(Reply reply) {
        var fault = FAULT_CODE.matcher(reply.body());
        return fault.find() ? "fault " + fault.group(1) : "status " + reply.status();
    }
}
