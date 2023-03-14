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

public interface AuditEventLoggingFacade {
    /**
     * Audit log an current request bound event success, if there is any event.
     * If there is no request bound event, does nothing.
     */
    void auditLogSuccess();

    /**
     * Audit log success of a specific event.
     * Does not touch request bound event.
     */
    void auditLogSuccess(RestApiAuditEvent event);


    /**
     * Audit log success of a specific event with a specific username.
     * Use this in exceptional situations, where usernameHelper does not contain username
     * (like failed form login)
     * Does not touch request bound event.
     *
     * @param username username to use for audit log
     */
    void auditLogSuccess(RestApiAuditEvent event, String username);

    /**
     * Audit log an event failure.
     * If there is no current event, logging is skipped
     *
     * @param ex exception to log
     */
    void auditLogFail(Exception ex);

    /**
     * Audit log an event failure.
     * Use this in exceptional situations where usernameHelper does not contain username
     * (like failed form login)
     * Does not touch request bound event.
     * Log using current request bound event, if any. If no request bound event, use defaultEvent
     *
     * @param defaultEvent event to use for logging in case request bound event does not exist
     * @param ex           exception related to event failure
     * @param username     username to use for audit log
     */
    void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex, String username);

    /**
     * Audit log an event failure.
     * Log using current request bound event, if any. If no request bound event, use defaultEvent
     *
     * @param defaultEvent event to use for logging in case request bound event does not exist
     * @param ex           exception to log
     */
    void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex);

    /**
     * Whether any of given events have been logged for this request. False if not in request scope.
     */
    boolean hasAlreadyLoggedForThisRequestAny(RestApiAuditEvent... events);

    /**
     * Whether given event has been logged for this request. False if not in request scope.
     */
    boolean hasAlreadyLoggedForThisRequest(RestApiAuditEvent event);
}
