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

import org.niis.xroad.restapi.util.RequestHelper;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.niis.xroad.restapi.util.UsernameHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class AuditEventLoggerMakeUpBetterName {

    // TO DO: remove after debugging that it works as expected
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

    private int instanceNumber;
    private final UsernameHelper usernameHelper;
    private final AuditEventLoggingFacade auditEventLoggingFacade;
    private final RequestHelper requestHelper;
    private final SecurityHelper securityHelper;

    // request scoped beans, may not have value if audit logging outside of requests
    private final AuditContextRequestScopeHolder auditContextRequestScopeHolder;
    private final RequestScopeLoggedEvents requestScopeLoggedEvents;

    @Autowired
    public AuditEventLoggerMakeUpBetterName(UsernameHelper usernameHelper,
            AuditEventLoggingFacade auditEventLoggingFacade,
            @Lazy AuditContextRequestScopeHolder auditContextRequestScopeHolder,
            @Lazy RequestScopeLoggedEvents requestScopeLoggedEvents,
            RequestHelper requestHelper,
            SecurityHelper securityHelper) {
        instanceNumber = INSTANCE_COUNTER.incrementAndGet();
        this.usernameHelper = usernameHelper;
        this.auditEventLoggingFacade = auditEventLoggingFacade;
        this.auditContextRequestScopeHolder = auditContextRequestScopeHolder;
        this.requestScopeLoggedEvents = requestScopeLoggedEvents;
        this.requestHelper = requestHelper;
        this.securityHelper = securityHelper;
    }

    public void setRequestScopedEvent(RestApiAuditEvent event) {
        if (requestHelper.requestScopeIsAvailable()) {
            RestApiAuditEvent existing = auditContextRequestScopeHolder.getRequestScopedEvent();
            if (existing != null) {
                throw new IllegalStateException("request scope already has event " + existing);
            } else {
                auditContextRequestScopeHolder.setRequestScopedEvent(event);
            }
        } else {
            throw new IllegalStateException("request scope is not available");
        }
    }

    public void putRequestScopedAuditData(String key, Object value) {
        if (requestHelper.requestScopeIsAvailable()) {
            auditContextRequestScopeHolder.getEventData().put(key, value);
        } else {
            throw new IllegalStateException("request scope is not available");
        }
    }

    /**
     * Audit log an current request bound event success, if there is any event.
     * If there is no request bound event, does nothing.
     */
    public void auditLogSuccess() {
        // TO DO: auditLogCurrentEventSuccess
        if (!hasAlreadyLoggedForThisRequest()) {
            if (getRequestScopedEvent() != null) {
                auditLog(getRequestScopedEvent(), usernameHelper.getUsername(),
                        addStandardEventData(getRequestScopedEventData()));
            }
        }
    }

    /**
     * Audit log success of a specific event.
     * Does not touch request bound event.
     */
    public void auditLogSuccess(RestApiAuditEvent event) {
        auditLog(event, usernameHelper.getUsername(), addStandardEventData(new HashMap<>()));
    }

    /**
     * Audit log an event failure.
     * If there is no current event, logging is skipped
     * @param ex
     */
    public void auditLogFail(Exception ex) {
        auditLogFailInternal(null, ex);
    }

    /**
     * Audit log an event failure.
     * Log using current request bound event, if any. If no request bound event, use defaultEvent
     * @param defaultEvent event to use for logging in case request bound event does not exist
     * @param ex
     */
    public void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex) {
        if (defaultEvent == null) {
            throw new IllegalArgumentException("missing defaultEvent");
        }
        auditLogFailInternal(defaultEvent, ex);
    }

    public boolean hasAlreadyLoggedForThisRequest() {
        if (requestHelper.requestScopeIsAvailable()) {
            return !getRequestScopedLoggedEvents().isEmpty();
        } else {
            return false;
        }
    }

    public boolean hasAlreadyLoggedForThisRequestAny(RestApiAuditEvent...events) {
        if (requestHelper.requestScopeIsAvailable()) {
            Set<RestApiAuditEvent> searchedEvents = Arrays.stream(events).collect(Collectors.toSet());
            return getRequestScopedLoggedEvents().stream().anyMatch(searchedEvents::contains);
        } else {
            return false;
        }
    }

    public boolean hasAlreadyLoggedForThisRequest(RestApiAuditEvent event) {
        if (requestHelper.requestScopeIsAvailable()) {
            return requestScopeLoggedEvents.getEvents().contains(event);
        } else {
            return false;
        }
    }

    private void auditLog(RestApiAuditEvent event, Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), data);
    }

    private void auditLog(RestApiAuditEvent event, String user, Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), user, data);
    }

    private void auditLog(RestApiAuditEvent event, String user, String reason,
            Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), user, reason, data);
    }

    private void addRequestScopedLoggedEventForThisRequest(RestApiAuditEvent event) {
        if (requestHelper.requestScopeIsAvailable()) {
            requestScopeLoggedEvents.getEvents().add(event);
        }
    }


    private RestApiAuditEvent getRequestScopedEvent() {
        if (requestHelper.requestScopeIsAvailable()) {
            return auditContextRequestScopeHolder.getRequestScopedEvent();
        } else {
            return null;
        }
    }

    /**
     * Get request scoped event data, or a new Map if not in request scope
     * @return
     */
    private Map<String, Object> getEventData() {
        if (requestHelper.requestScopeIsAvailable()) {
            return getRequestScopedEventData();
        } else {
            return new HashMap<>();
        }
    }

    private Map<String, Object> getRequestScopedEventData() {
        if (requestHelper.requestScopeIsAvailable()) {
            return auditContextRequestScopeHolder.getEventData();
        } else {
            return null;
        }
    }

    private Set<RestApiAuditEvent> getRequestScopedLoggedEvents() {
        if (requestHelper.requestScopeIsAvailable()) {
            return requestScopeLoggedEvents.getEvents();
        } else {
            return null;
        }
    }



    /**
     * Adds url and authentication method to event data map. Does not modify original map, returns a new instance
     */
    private Map<String, Object> addStandardEventData(Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>(data);
        result.put("url", requestHelper.getCurrentRequestUrl());
        result.put("auth", securityHelper.getCurrentAuthenticationScheme());
        return result;
    }

    private void auditLogFailInternal(RestApiAuditEvent defaultEvent, Exception ex) {
        RestApiAuditEvent eventToLog = getRequestScopedEvent();
        if (eventToLog == null) {
            eventToLog = defaultEvent;
        }
        if (eventToLog != null) {
            Map<String, Object> data = addStandardEventData(getEventData());
            String reason = ex.getMessage();
            auditLog(eventToLog, usernameHelper.getUsername(), reason, data);
        }
    }

}
