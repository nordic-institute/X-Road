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
package ee.ria.xroad.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:MagicNumber")
public enum HttpStatus {

    // ===== 1XX INFORMATIONAL =====
    CONTINUE(100, "Continue"),
    SWITCHING_PROTOCOLS(101, "Switching Protocols"),
    PROCESSING(102, "Processing"),

    // ===== 2XX SUCCESS =====
    OK(200, "OK"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NON_AUTHORITATIVE_INFORMATION(203, "Non-Authoritative Information"),
    NO_CONTENT(204, "No Content"),
    RESET_CONTENT(205, "Reset Content"),
    PARTIAL_CONTENT(206, "Partial Content"),
    MULTI_STATUS(207, "Multi-Status"),

    // ===== 3XX REDIRECTION =====
    MULTIPLE_CHOICES(300, "Multiple Choices"),
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    MOVED_TEMPORARILY(302, "Moved Temporarily"),
    SEE_OTHER(303, "See Other"),
    NOT_MODIFIED(304, "Not Modified"),
    USE_PROXY(305, "Use Proxy"),
    TEMPORARY_REDIRECT(307, "Temporary Redirect"),

    // ===== 4XX CLIENT ERRORS =====
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    PAYMENT_REQUIRED(402, "Payment Required"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    PROXY_AUTHENTICATION_REQUIRED(407, "Proxy Authentication Required"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    REQUEST_TOO_LONG(413, "Request Too Long"),
    REQUEST_URI_TOO_LONG(414, "Request-URI Too Long"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    REQUESTED_RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
    EXPECTATION_FAILED(417, "Expectation Failed"),
    INSUFFICIENT_SPACE_ON_RESOURCE(419, "Insufficient Space On Resource"),
    METHOD_FAILURE(420, "Method Failure"),
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
    LOCKED(423, "Locked"),
    FAILED_DEPENDENCY(424, "Failed Dependency"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),

    // ===== 5XX SERVER ERRORS =====
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    BAD_GATEWAY(502, "Bad Gateway"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    GATEWAY_TIMEOUT(504, "Gateway Timeout"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported"),
    INSUFFICIENT_STORAGE(507, "Insufficient Storage");

    private final int code;
    private final String reasonPhrase;

    /**
     * Check if this status code represents a successful response (2xx).
     *
     * @return true if the status code is in the 2xx range
     */
    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    /**
     * Check if this status code represents a client error (4xx).
     *
     * @return true if the status code is in the 4xx range
     */
    public boolean isClientError() {
        return code >= 400 && code < 500;
    }

    /**
     * Check if this status code represents a server error (5xx).
     *
     * @return true if the status code is in the 5xx range
     */
    public boolean isServerError() {
        return code >= 500 && code < 600;
    }

    /**
     * Check if this status code represents an error (4xx or 5xx).
     *
     * @return true if the status code is in the 4xx or 5xx range
     */
    public boolean isError() {
        return isClientError() || isServerError();
    }

    /**
     * Check if this status code represents a redirection (3xx).
     *
     * @return true if the status code is in the 3xx range
     */
    public boolean isRedirection() {
        return code >= 300 && code < 400;
    }

    /**
     * Check if this status code represents an informational response (1xx).
     *
     * @return true if the status code is in the 1xx range
     */
    public boolean isInformational() {
        return code >= 100 && code < 200;
    }

    /**
     * Get HttpStatus from numeric code.
     *
     * @param code the numeric status code
     * @return the HttpStatus enum value, or null if not found
     */
    public static HttpStatus fromCode(int code) {
        for (HttpStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * Get HttpStatus from numeric code with fallback.
     *
     * @param code     the numeric status code
     * @param fallback the fallback status to return if code not found
     * @return the HttpStatus enum value, or fallback if not found
     */
    public static HttpStatus fromCode(int code, HttpStatus fallback) {
        HttpStatus status = fromCode(code);
        return status != null ? status : fallback;
    }

    /**
     * Check if a numeric code represents a successful response (2xx).
     *
     * @param code the numeric status code
     * @return true if the status code is in the 2xx range
     */
    public static boolean isSuccess(int code) {
        return code >= 200 && code < 300;
    }

    /**
     * Check if a numeric code represents a client error (4xx).
     *
     * @param code the numeric status code
     * @return true if the status code is in the 4xx range
     */
    public static boolean isClientError(int code) {
        return code >= 400 && code < 500;
    }

    /**
     * Check if a numeric code represents a server error (5xx).
     *
     * @param code the numeric status code
     * @return true if the status code is in the 5xx range
     */
    public static boolean isServerError(int code) {
        return code >= 500 && code < 600;
    }

    /**
     * Check if a numeric code represents an error (4xx or 5xx).
     *
     * @param code the numeric status code
     * @return true if the status code is in the 4xx or 5xx range
     */
    public static boolean isError(int code) {
        return isClientError(code) || isServerError(code);
    }

    @Override
    public String toString() {
        return code + " " + reasonPhrase;
    }
}
