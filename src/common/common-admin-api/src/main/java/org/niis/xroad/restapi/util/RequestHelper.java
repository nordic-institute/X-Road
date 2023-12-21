/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static ee.ria.xroad.common.util.HttpHeaders.X_FORWARDED_FOR;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Helper for working with requests
 */
@Component
public final class RequestHelper {

    public String getCurrentRequestUrl() {
        HttpServletRequest currentRequest = getCurrentHttpRequest();
        if (currentRequest != null) {
            return currentRequest.getRequestURI();
        } else {
            return null;
        }
    }

    /**
     * Returns current {@link HttpServletRequest}
     * @return
     */
    public HttpServletRequest getCurrentHttpRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) requestAttributes).getRequest();
        }
        return null;
    }

    /**
     * Tells if request scoped beans are available or not
     * (if we're executing a http request, or not)
     */
    public boolean requestScopeIsAvailable() {
        return RequestContextHolder.getRequestAttributes() != null;
    }

    /**
     * Run operation (that returns some value) in request scope,
     * or throw {@link IllegalStateException} if there is not request scope
     * @param operation
     */
    public Object runInRequestScope(RequestScopeOperation operation) {
        if (requestScopeIsAvailable()) {
            return operation.executeInRequest();
        } else {
            throw new IllegalStateException("request scope is not available");
        }
    }

    /**
     * Run a void operation in request scope,
     * or throw {@link IllegalStateException} if there is not request scope
     * @param operation
     */
    public void runInRequestScope(RequestScopeVoidOperation operation) {
        if (requestScopeIsAvailable()) {
            operation.executeInRequest();
        } else {
            throw new IllegalStateException("request scope is not available");
        }
    }

    public interface RequestScopeOperation {
        Object executeInRequest();
    }

    public interface RequestScopeVoidOperation {
        void executeInRequest();
    }

    /**
     * Gets request senders IP address
     */
    public String getRequestSenderIPAddress() {
        String ipAddress = null;
        final HttpServletRequest currentHttpRequest = getCurrentHttpRequest();
        if (currentHttpRequest != null) {
            ipAddress = currentHttpRequest.getHeader(X_FORWARDED_FOR);
            if (isBlank(ipAddress)) {
                ipAddress = currentHttpRequest.getRemoteAddr();
            }
        }
        return ipAddress;
    }
}
