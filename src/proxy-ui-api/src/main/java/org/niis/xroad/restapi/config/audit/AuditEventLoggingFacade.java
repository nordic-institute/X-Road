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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facade for all proxy-ui-api audit logging.
 * When used from request aware context, keeps track of current tracked audit event, and it's associated audit data.
 * Also adjusts to non-request aware context.
 */
@Component
@Profile("nontest")
public class AuditEventLoggingFacade {

    private final UsernameHelper usernameHelper;
    private final RequestHelper requestHelper;
    private final SecurityHelper securityHelper;

    private final RequestScopedAuditDataHolder requestScopedAuditDataHolder;

    @Autowired
    public AuditEventLoggingFacade(UsernameHelper usernameHelper,
            RequestScopedAuditDataHolder requestScopedAuditDataHolder,
            RequestHelper requestHelper,
            SecurityHelper securityHelper) {
        this.usernameHelper = usernameHelper;
        this.requestScopedAuditDataHolder = requestScopedAuditDataHolder;
        this.requestHelper = requestHelper;
        this.securityHelper = securityHelper;
    }

    /**
     * Audit log an current request bound event success, if there is any event.
     * If there is no request bound event, does nothing.
     */
    public void auditLogSuccess() {
        if (getRequestScopedEvent() != null) {
            auditLog(getRequestScopedEvent(), usernameHelper.getUsername(),
                    getRequestScopedEventData());
        }
    }

    /**
     * Audit log success of a specific event.
     * Does not touch request bound event.
     */
    public void auditLogSuccess(RestApiAuditEvent event) {
        auditLog(event, usernameHelper.getUsername(), getEventData());
    }

    /**
     * Audit log success of a specific event with a specific username.
     * Use this in exceptional situations, where usernameHelper does not contain username
     * (like failed form login)
     * Does not touch request bound event.
     * @param username username to use for audit log
     */
    public void auditLogSuccess(RestApiAuditEvent event, String username) {
        auditLog(event, username, getEventData());
    }

    /**
     * Audit log an event failure.
     * If there is no current event, logging is skipped
     * @param ex
     */
    public void auditLogFail(Exception ex) {
        auditLogFailInternal(null, ex, null);
    }

    /**
     * Audit log an event failure.
     * Use this in exceptional situations, where usernameHelper does not contain username
     * (like failed form login)
     * Does not touch request bound event.
     * Log using current request bound event, if any. If no request bound event, use defaultEvent
     * @param defaultEvent event to use for logging in case request bound event does not exist
     * @param ex exception related to event failure
     * @param username username to use for audit log
     */
    public void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex, String username) {
        if (defaultEvent == null) {
            throw new IllegalArgumentException("missing defaultEvent");
        }
        if (username == null) {
            throw new IllegalArgumentException("missing username");
        }
        auditLogFailInternal(defaultEvent, ex, username);
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
        auditLogFailInternal(defaultEvent, ex, null);
    }

    public boolean hasAlreadyLoggedForThisRequest() {
        if (requestHelper.requestScopeIsAvailable()) {
            return !requestScopedAuditDataHolder.getLoggedEvents().isEmpty();
        } else {
            return false;
        }
    }

    public boolean hasAlreadyLoggedForThisRequestAny(RestApiAuditEvent...events) {
        if (requestHelper.requestScopeIsAvailable()) {
            Set<RestApiAuditEvent> searchedEvents = Arrays.stream(events).collect(Collectors.toSet());
            return requestScopedAuditDataHolder.getLoggedEvents().stream().anyMatch(searchedEvents::contains);
        } else {
            return false;
        }
    }

    public boolean hasAlreadyLoggedForThisRequest(RestApiAuditEvent event) {
        if (requestHelper.requestScopeIsAvailable()) {
            return requestScopedAuditDataHolder.getLoggedEvents().contains(event);
        } else {
            return false;
        }
    }

    private void auditLog(RestApiAuditEvent event, String user, Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), user, data,
                securityHelper.getCurrentAuthenticationScheme(),
                requestHelper.getCurrentRequestUrl());
    }

    private void auditLog(RestApiAuditEvent event, String user, String reason,
            Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        AuditLogger.log(event.getEventName(), user, reason, data,
                securityHelper.getCurrentAuthenticationScheme(),
                requestHelper.getCurrentRequestUrl());
    }

    private void addRequestScopedLoggedEventForThisRequest(RestApiAuditEvent event) {
        if (requestHelper.requestScopeIsAvailable()) {
            requestScopedAuditDataHolder.getLoggedEvents().add(event);
        }
    }


    private RestApiAuditEvent getRequestScopedEvent() {
        if (requestHelper.requestScopeIsAvailable()) {
            return requestScopedAuditDataHolder.getAuditEvent();
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
            return requestScopedAuditDataHolder.getEventData();
        } else {
            return null;
        }
    }

    private void auditLogFailInternal(RestApiAuditEvent defaultEvent, Exception ex, String usernameOverride) {
        String username = usernameOverride;
        if (username == null) {
            username = usernameHelper.getUsername();
        }
        RestApiAuditEvent eventToLog = getRequestScopedEvent();
        if (eventToLog == null) {
            eventToLog = defaultEvent;
        }
        if (eventToLog != null) {
            String reason = ex.getMessage();
            auditLog(eventToLog, username, reason, getEventData());
        }
    }

}
