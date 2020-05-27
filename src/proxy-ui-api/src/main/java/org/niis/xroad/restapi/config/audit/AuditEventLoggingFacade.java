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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Facade for {@link AuditLogger}, implements all proxy-ui-api audit logging calls.
 * Tracks logged events and stores them in {@link RequestScopedAuditDataHolder}
 * Knows which events have been audit logged so far (can be queried with e.g. hasAlreadyLoggedForThisRequestAny).
 * Also adjusts to non-request aware context.
 */
@Component
@Profile({ "nontest"})
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
                    createConvertedEventData());
        }
    }

    /**
     * Audit log success of a specific event.
     * Does not touch request bound event.
     */
    public void auditLogSuccess(RestApiAuditEvent event) {
        auditLog(event, usernameHelper.getUsername(), createConvertedEventData());
    }

    /**
     * Audit log success of a specific event with a specific username.
     * Use this in exceptional situations, where usernameHelper does not contain username
     * (like failed form login)
     * Does not touch request bound event.
     * @param username username to use for audit log
     */
    public void auditLogSuccess(RestApiAuditEvent event, String username) {
        auditLog(event, username, createConvertedEventData());
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
     * Use this in exceptional situations where usernameHelper does not contain username
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

    /**
     * Whether any audit logging has been done for this request. False if not in request scope.
     * @return
     */
    public boolean hasAlreadyLoggedForThisRequest() {
        if (requestHelper.requestScopeIsAvailable()) {
            return !requestScopedAuditDataHolder.getLoggedEvents().isEmpty();
        } else {
            return false;
        }
    }

    /**
     * Whether any of given events have been logged for this request. False if not in request scope.
     * @return
     */
    public boolean hasAlreadyLoggedForThisRequestAny(RestApiAuditEvent...events) {
        if (requestHelper.requestScopeIsAvailable()) {
            Set<RestApiAuditEvent> searchedEvents = Arrays.stream(events).collect(Collectors.toSet());
            return requestScopedAuditDataHolder.getLoggedEvents().stream().anyMatch(searchedEvents::contains);
        } else {
            return false;
        }
    }

    /**
     * Whether given event has been logged for this request. False if not in request scope.
     * @return
     */
    public boolean hasAlreadyLoggedForThisRequest(RestApiAuditEvent event) {
        if (requestHelper.requestScopeIsAvailable()) {
            return requestScopedAuditDataHolder.getLoggedEvents().contains(event);
        } else {
            return false;
        }
    }

    private void auditLog(RestApiAuditEvent event, String user, Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        callAuditLoggerLogSuccess(event, user, data, securityHelper.getCurrentAuthenticationScheme(),
                requestHelper.getCurrentRequestUrl());
    }

    private void auditLog(RestApiAuditEvent event, String user, String reason,
            Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        callAuditLoggerLogFailure(event, user, reason, data, securityHelper.getCurrentAuthenticationScheme(),
                requestHelper.getCurrentRequestUrl());
    }

    // package private wrapper method for AuditLogger.log, to enable easier mocking in tests
    void callAuditLoggerLogSuccess(RestApiAuditEvent event, String user, Map<String, Object> data,
            String auth, String url) {
        AuditLogger.log(event.getEventName(), user, data, auth, url);
    }

    // package private wrapper method for AuditLogger.log, to enable easier mocking in tests
    void callAuditLoggerLogFailure(RestApiAuditEvent event, String user, String reason,
            Map<String, Object> data, String auth, String url) {
        AuditLogger.log(event.getEventName(), user, reason, data, auth, url);
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
     * Get request scoped event data, or a new empty Map if not in request scope.
     * Map contains property name strings as keys instead of RestApiAuditProperties (hence "Converted").
     * Use this for producing data object that will be given to actual {@link AuditLogger}
     */
    private Map<String, Object> createConvertedEventData() {
        if (requestHelper.requestScopeIsAvailable()) {
            return convertPropertyMap(requestScopedAuditDataHolder.getEventData());
        } else {
            return new HashMap<>();
        }
    }

    /**
     * Convert a map with RestApiAuditProperty keys to a map with RestApiAuditProperty.getPropertyName as keys
     */
    private Map<String, Object> convertPropertyMap(Map<RestApiAuditProperty, Object> map) {
        LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
        for (RestApiAuditProperty prop : map.keySet()) {
            converted.put(prop.getPropertyName(), map.get(prop));
        }
        return converted;
    }

    /**
     * Audit logs an failure. Some helper logic for handling handling different username and event parameters.
     * Logs either request scoped event, or given defaultEvent, or skips logging if neither exists.
     * @param defaultEvent {@link RestApiAuditEvent} that will be used, if there is no request scoped event
     * @param ex exception whose message is used as failure reason
     * @param usernameOverride username to log. If null, username associated with current
     * {@link org.springframework.security.core.Authentication} is used. For exceptional cases where Authentication
     *                         does not (yet?) contain username
     */
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
            auditLog(eventToLog, username, reason, createConvertedEventData());
        }
    }

}
