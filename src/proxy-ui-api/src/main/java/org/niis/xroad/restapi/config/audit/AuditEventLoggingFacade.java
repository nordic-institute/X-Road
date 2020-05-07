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

import ee.ria.xroad.common.AuditLogger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

/**
 * A simple request scoped facade for audit logging.
 * Helps prevent multiple audit log entries due to same event
 * (e.g. wrong API key value causing both "API key was not found" and "Authentication not found")
 */
@Component
@Slf4j
public class AuditEventLoggingFacade {

    private final RequestScopeLoggedEvents requestScopeLoggedEvents;

    @Autowired
    public AuditEventLoggingFacade(@Lazy RequestScopeLoggedEvents requestScopeLoggedEvents) {
        this.requestScopeLoggedEvents = requestScopeLoggedEvents;
    }

    public boolean hasLoggedForThisRequest() {
        if (requestScopeIsAvailable()) {
            return !requestScopeLoggedEvents.getEvents().isEmpty();
        } else {
            return false;
        }
    }

    /**
     * Tells if request scoped beans are available or not
     * (if we're executing a http request, or not
     */
    private boolean requestScopeIsAvailable() {
        return RequestContextHolder.getRequestAttributes() != null;
    }

    public boolean hasLoggedForThisRequest(RestApiAuditEvent event) {
        if (requestScopeIsAvailable()) {
            return requestScopeLoggedEvents.getEvents().contains(event);
        } else {
            return false;
        }
    }

    private void addLoggedEventForThisRequest(RestApiAuditEvent event) {
        if (requestScopeIsAvailable()) {
            requestScopeLoggedEvents.getEvents().add(event);
        }
    }

    // TO DO: comments
    public void log(RestApiAuditEvent event, Map<String, Object> data) {
        addLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), data);
    }

    public void log(RestApiAuditEvent event, String user, Map<String, Object> data) {
        addLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), user, data);
    }

    public void log(RestApiAuditEvent event, String user, String reason,
            Map<String, Object> data) {
        addLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), user, reason, data);
    }

    private void alreadyAuditLogged(RestApiAuditEvent event) {
            log.info("Skipping audit logging for event " + event
                    + " since audit event has already been logged for this request");
    }

    public void logOncePerRequest(RestApiAuditEvent event, Map<String, Object> data) {
        if (hasLoggedForThisRequest()) {
            alreadyAuditLogged(event);
        } else {
            log(event, data);
        }
    }

    public void logOncePerRequest(RestApiAuditEvent event, String user, Map<String, Object> data) {
        if (hasLoggedForThisRequest()) {
            alreadyAuditLogged(event);
        } else {
            log(event, user, data);
        }
    }

    public void logOncePerRequest(RestApiAuditEvent event, String user, String reason,
            Map<String, Object> data) {
        if (hasLoggedForThisRequest()) {
            alreadyAuditLogged(event);
        } else {
            log(event, user, reason, data);
        }
    }

}
