/**
 * The MIT License
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
package org.niis.xroad.restapi.config.audit;

import org.niis.xroad.restapi.util.RequestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Helper that holds all request scoped audit data.
 * If methods for accessing the data are used ouside of request scope,
 * {@link IllegalStateException} is thrown.
 *
 * Can be injected to beans that are used (also) outside request scope.
 */
@Component
class RequestScopedAuditDataHolder {

    private final RequestScopedAuditEventData requestScopedAuditEventData;
    private final RequestScopedLoggedAuditEvents requestScopedLoggedAuditEvents;
    private final RequestHelper requestHelper;

    @Autowired
    RequestScopedAuditDataHolder(@Lazy RequestScopedAuditEventData requestScopedAuditEventData,
            @Lazy RequestScopedLoggedAuditEvents requestScopedLoggedAuditEvents,
            RequestHelper requestHelper) {
        this.requestScopedLoggedAuditEvents = requestScopedLoggedAuditEvents;
        this.requestScopedAuditEventData = requestScopedAuditEventData;
        this.requestHelper = requestHelper;
    }

    void setAuditEvent(RestApiAuditEvent event) {
        requestHelper.runInRequestScope(() ->
                requestScopedAuditEventData.setRequestScopedEvent(event));
    }

    RestApiAuditEvent getAuditEvent() {
        return (RestApiAuditEvent) requestHelper.runInRequestScope(() ->
            requestScopedAuditEventData.getRequestScopedEvent());
    }

    Map<RestApiAuditProperty, Object> getEventData() {
        return (Map<RestApiAuditProperty, Object>) requestHelper.runInRequestScope(() ->
                requestScopedAuditEventData.getEventData());
    }

    Set<RestApiAuditEvent> getLoggedEvents() {
        return (Set<RestApiAuditEvent>) requestHelper.runInRequestScope(() ->
                requestScopedLoggedAuditEvents.getEvents());
    }
}
