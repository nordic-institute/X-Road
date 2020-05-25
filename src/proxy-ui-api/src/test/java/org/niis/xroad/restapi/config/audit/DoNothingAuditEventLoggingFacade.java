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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DoNothingAuditEventLoggingFacade extends AuditEventLoggingFacade {
    @Autowired
    public DoNothingAuditEventLoggingFacade() {
        super(null, null, null, null, null);
    }

    @Override
    public void initRequestScopedEvent(RestApiAuditEvent event) {
    }

    @Override
    public void changeRequestScopedEvent(RestApiAuditEvent event) {
    }

    @Override
    public boolean hasRequestScopedEvent(RestApiAuditEvent event) {
        return true;
    }

    @Override
    public void putRequestScopedAuditData(String key, Object value) {
    }

    @Override
    public void putRequestScopedAuditData(RestApiAuditProperty auditProperty, Object value) {
    }

    @Override
    public void addRequestScopedAuditListData(RestApiAuditProperty auditProperty, Object value) {
    }

    @Override
    public void auditLogSuccess() {
    }

    @Override
    public void auditLogSuccess(RestApiAuditEvent event) {
    }

    @Override
    public void auditLogSuccess(RestApiAuditEvent event, String username) {
    }

    @Override
    public void auditLogFail(Exception ex) {
    }

    @Override
    public void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex, String username) {
    }

    @Override
    public void auditLogFail(RestApiAuditEvent defaultEvent, Exception ex) {
    }

    @Override
    public boolean hasAlreadyLoggedForThisRequest() {
        return true;
    }

    @Override
    public boolean hasAlreadyLoggedForThisRequestAny(RestApiAuditEvent... events) {
        return true;
    }

    @Override
    public boolean hasAlreadyLoggedForThisRequest(RestApiAuditEvent event) {
        return true;
    }
}
