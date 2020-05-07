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

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

/**
 * A simple request scoped facade for audit logging.
 * Helps prevent multiple audit log entries due to same event
 * (e.g. wrong API key value causing both "API key was not found" and "Autentication not found")
 */
@Getter
@Setter
@Component
@Scope(SCOPE_REQUEST)
public class AuditEventLoggingFacade {

    private Set<RestApiAuditEvent> loggedEvents = new HashSet<>();

    public boolean hasLogged() {
        return !loggedEvents.isEmpty();
    }

    public boolean hasLogged(RestApiAuditEvent event) {
        return loggedEvents.contains(event);
    }

    // TO DO: comments
    public void log(RestApiAuditEvent event, Map<String, Object> data) {
        loggedEvents.add(event);
        AuditLogger.log(event.getEventName(), data);
    }

    public void log(RestApiAuditEvent event, String user, Map<String, Object> data) {
        loggedEvents.add(event);
        AuditLogger.log(event.getEventName(), user, data);
    }

    public void log(RestApiAuditEvent event, String user, String reason,
            Map<String, Object> data) {
        loggedEvents.add(event);
        AuditLogger.log(event.getEventName(), user, reason, data);
    }
}
