/**
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
package org.niis.xroad.restapi.config.audit;

import ee.ria.xroad.common.AuditLogger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
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
 * Facade for {@link AuditLogger}, implements all spring-boot based APIs audit logging calls.
 * Tracks logged events and stores them in {@link RequestScopedAuditDataHolder}
 * Knows which events have been audit logged so far (can be queried with e.g. hasAlreadyLoggedForThisRequestAny).
 * Also adjusts to non-request aware context.
 */
@Component
@Profile({"nontest", "audit-test"})
public class AuditEventLoggingFacadeImpl implements AuditEventLoggingFacade {

    private final UsernameHelper usernameHelper;
    private final RequestHelper requestHelper;
    private final SecurityHelper securityHelper;

    private final RequestScopedAuditDataHolder requestScopedAuditDataHolder;

    @Autowired
    public AuditEventLoggingFacadeImpl(UsernameHelper usernameHelper,
                                       RequestScopedAuditDataHolder requestScopedAuditDataHolder,
                                       RequestHelper requestHelper,
                                       SecurityHelper securityHelper) {
        this.usernameHelper = usernameHelper;
        this.requestScopedAuditDataHolder = requestScopedAuditDataHolder;
        this.requestHelper = requestHelper;
        this.securityHelper = securityHelper;
    }


    @Override
    public void auditLogSuccess() {
        if (getRequestScopedEvent() != null) {
            auditLog(getRequestScopedEvent(), usernameHelper.getUsername(), requestHelper.getRequestSenderIPAddress(),
                    createConvertedEventData());
        }
    }


    @Override
    public void auditLogSuccess(RestApiAuditEvent event) {
        auditLog(event, usernameHelper.getUsername(), requestHelper.getRequestSenderIPAddress(), createConvertedEventData());
    }

    @Override
    public void auditLogSuccess(RestApiAuditEvent event, String username) {
        auditLog(event, username, requestHelper.getRequestSenderIPAddress(), createConvertedEventData());
    }

    @Override
    public void auditLogFail(Exception ex) {
        auditLogFailInternal(null, ex, null);
    }


    @Override
    public void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex, String username) {
        if (defaultEvent == null) {
            throw new IllegalArgumentException("missing defaultEvent");
        }
        if (username == null) {
            throw new IllegalArgumentException("missing username");
        }
        auditLogFailInternal(defaultEvent, ex, username);
    }


    @Override
    public void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex) {
        if (defaultEvent == null) {
            throw new IllegalArgumentException("missing defaultEvent");
        }
        auditLogFailInternal(defaultEvent, ex, null);
    }

    @Override
    public boolean hasAlreadyLoggedForThisRequestAny(RestApiAuditEvent... events) {
        if (requestHelper.requestScopeIsAvailable()) {
            Set<RestApiAuditEvent> searchedEvents = Arrays.stream(events).collect(Collectors.toSet());
            return requestScopedAuditDataHolder.getLoggedEvents().stream().anyMatch(searchedEvents::contains);
        } else {
            return false;
        }
    }


    @Override
    public boolean hasAlreadyLoggedForThisRequest(RestApiAuditEvent event) {
        if (requestHelper.requestScopeIsAvailable()) {
            return requestScopedAuditDataHolder.getLoggedEvents().contains(event);
        } else {
            return false;
        }
    }

    private void auditLog(RestApiAuditEvent event, String user, String ipAddress, Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        callAuditLoggerLogSuccess(event, user, ipAddress, data,
                securityHelper.getCurrentAuthenticationScheme(), requestHelper.getCurrentRequestUrl());
    }

    private void auditLogFailure(RestApiAuditEvent event, String user, String ipAddress, String reason,
                                 Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        callAuditLoggerLogFailure(event, user, ipAddress, reason, data,
                securityHelper.getCurrentAuthenticationScheme(), requestHelper.getCurrentRequestUrl());
    }

    private void auditLogWarning(RestApiAuditEvent event, String user, String ipAddress, String reason,
                                 Map<String, Object> data) {
        addRequestScopedLoggedEventForThisRequest(event);
        callAuditLoggerLogWarning(event, user, ipAddress, reason, data,
                securityHelper.getCurrentAuthenticationScheme(), requestHelper.getCurrentRequestUrl());
    }

    public void callAuditLoggerLogSuccess(RestApiAuditEvent event, String user, String ipAddress, Map<String, Object> data,
                                          String auth, String url) {
        AuditLogger.log(event.getEventName(), user, ipAddress, data, auth, url);
    }

    public void callAuditLoggerLogFailure(RestApiAuditEvent event, String user, String ipAddress, String reason,
                                          Map<String, Object> data, String auth, String url) {
        AuditLogger.log(event.getEventName(), user, ipAddress, reason, data, auth, url);
    }

    public void callAuditLoggerLogWarning(RestApiAuditEvent event, String user, String ipAddress, String reason,
                                          Map<String, Object> data, String auth, String url) {
        AuditLogger.logWarning(event.getEventName(), user, ipAddress, reason, data, auth, url);
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
        map.forEach((key, value) -> converted.put(key.getPropertyName(), value));
        return converted;
    }

    /**
     * Audit logs a failure. Some helper logic for handling different username and event parameters.
     * Logs either request scoped event, or given defaultEvent, or skips logging if neither exists.
     * Based on exception root causes, calls proper AuditLogger method which includes boolean indicating if
     * failure was caused by unhandled warnings
     *
     * @param defaultEvent     {@link RestApiAuditEvent} that will be used, if there is no request scoped event
     * @param ex               exception whose message is used as failure reason
     * @param usernameOverride username to log. If null, username associated with current
     *                         {@link org.springframework.security.core.Authentication} is used. For exceptional cases where Authentication
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
            if (causedByUnhandledWarnings(ex)) {
                auditLogWarning(eventToLog, username, requestHelper.getRequestSenderIPAddress(), reason, createConvertedEventData());
            } else {
                auditLogFailure(eventToLog, username, requestHelper.getRequestSenderIPAddress(), reason, createConvertedEventData());
            }
        }
    }


    /**
     * Finds out if exception causes contain UnhandledWarningsException
     */
    private boolean causedByUnhandledWarnings(Throwable t) {
        return ExceptionUtils.indexOfType(t, UnhandledWarningsException.class) != -1;
    }

}
