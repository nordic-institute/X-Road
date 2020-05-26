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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Helper that holds request scoped audit data
 */
@Component
@Profile("nontest")
public class RequestScopedAuditDataHolder {

    private final AuditContextRequestScopeHolder auditContextRequestScopeHolder;
    private final RequestScopeLoggedEvents requestScopeLoggedEvents;
    private final RequestHelper requestHelper;

    @Autowired
    public RequestScopedAuditDataHolder(@Lazy AuditContextRequestScopeHolder auditContextRequestScopeHolder,
            @Lazy RequestScopeLoggedEvents requestScopeLoggedEvents,
            RequestHelper requestHelper) {
        this.requestScopeLoggedEvents = requestScopeLoggedEvents;
        this.auditContextRequestScopeHolder = auditContextRequestScopeHolder;
        this.requestHelper = requestHelper;
    }

    public void setAuditEvent(RestApiAuditEvent event) {
        requestHelper.runInRequestScope(() ->
                auditContextRequestScopeHolder.setRequestScopedEvent(event));
    }

    public RestApiAuditEvent getAuditEvent() {
        return (RestApiAuditEvent) requestHelper.runInRequestScope(() ->
            auditContextRequestScopeHolder.getRequestScopedEvent());
    }

    public Map<String, Object> getEventData() {
        return (Map<String, Object>) requestHelper.runInRequestScope(() ->
                auditContextRequestScopeHolder.getEventData());
    }

    public void addData(String propertyName, Object value) {
        requestHelper.runInRequestScope(() ->
                auditContextRequestScopeHolder.getEventData().put(propertyName, value));
    }

    public Set<RestApiAuditEvent> getLoggedEvents() {
        return (Set<RestApiAuditEvent>) requestHelper.runInRequestScope(() ->
                requestScopeLoggedEvents.getEvents());
    }
}
