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

import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.restapi.util.UsernameHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Getter
@Setter
@Component
@Scope(SCOPE_REQUEST)
public class AuditEventHolder {

    // TO DO: remove after debugging that it works as expected
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);
    // TODO: no setter for requestScopedEvent. Instead init(event) and change(event) if needed?
    private RestApiAuditEvent requestScopedEvent;
    private Map<String, Object> eventData = new HashMap<>();

    private int instanceNumber;
    private final UsernameHelper usernameHelper;
    private final AuditEventLoggingFacade auditEventLoggingFacade;

    @Autowired
    public AuditEventHolder(UsernameHelper usernameHelper,
            AuditEventLoggingFacade auditEventLoggingFacade) {
        instanceNumber = INSTANCE_COUNTER.incrementAndGet();
        this.usernameHelper = usernameHelper;
        this.auditEventLoggingFacade = auditEventLoggingFacade;
    }

    public void addData(String propertyName, Object value) {
        eventData.put(propertyName, value);
    }

    /**
     * Audit log an event success, if there is any event. If no request bound event, do nothing.
     */
    public void auditLogSuccess() {
        if (!auditEventLoggingFacade.hasLoggedForThisRequest()) {
            if (getRequestScopedEvent() != null) {
                addStandardEventData();
                auditEventLoggingFacade.log(requestScopedEvent, usernameHelper.getUsername(), getEventData());
            }
        }
    }

    /**
     * Adds url and authentication method.
     */
    private void addStandardEventData() {
        eventData.put("url", getCurrentRequestUrl());
        eventData.put("auth", getCurrentAuthenticationScheme());
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

    // TO DO: refactor
    public static String getCurrentRequestUrl() {
        return getCurrentHttpRequest().getRequestURI();
    }

    private String getCurrentAuthenticationScheme() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Authentication authentication = securityContext.getAuthentication();
        if (authentication == null) {
            return null;
        } else if (authentication instanceof PreAuthenticatedAuthenticationToken) {
            return "ApiKey";
        } else if (authentication instanceof UsernamePasswordAuthenticationToken) {
            if (hasSecurityContextInSession()) {
                return "Session";
            } else {
                return "HttpBasicPam";
            }
        } else {
            return authentication.getClass().getSimpleName();
        }
    }

    private boolean hasSecurityContextInSession() {
        HttpServletRequest request = getCurrentHttpRequest();
        boolean hasSessionContext = false;
        if (request != null) {
            hasSessionContext = new HttpSessionSecurityContextRepository().containsContext(request);
        }
        return hasSessionContext;
    }

    private void auditLogFailInternal(RestApiAuditEvent defaultEvent, Exception ex) {
        RestApiAuditEvent eventToLog = getRequestScopedEvent();
        if (eventToLog == null) {
            eventToLog = defaultEvent;
        }
        if (eventToLog != null) {
            addStandardEventData();
            String reason = ex.getMessage();
            auditEventLoggingFacade.log(eventToLog, usernameHelper.getUsername(), reason, getEventData());
        }
    }

    private static HttpServletRequest getCurrentHttpRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes)requestAttributes).getRequest();
            return request;
        }
        return null;
    }
}
