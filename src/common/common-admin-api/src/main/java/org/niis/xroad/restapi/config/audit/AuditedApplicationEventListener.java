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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.event.AuthenticationCredentialsNotFoundEvent;
import org.springframework.stereotype.Component;

/**
 * Listener that enables detailed logging of AuthenticationCredentialsNotFoundEvents
 */
@Component
@Slf4j
public class AuditedApplicationEventListener {

    private final AuditEventLoggingFacade auditEventLoggingFacade;

    @Autowired
    public AuditedApplicationEventListener(AuditEventLoggingFacade auditEventLoggingFacade) {
        this.auditEventLoggingFacade = auditEventLoggingFacade;
    }

    @EventListener
    void handleAuthenticationCredentialsNotFoundEvent(AuthenticationCredentialsNotFoundEvent event) {
        // prevent double audit logging both API_KEY_AUTHENTICATION and AUTH_CREDENTIALS_DISCOVERY
        if (!auditEventLoggingFacade.hasAlreadyLoggedForThisRequest(RestApiAuditEvent.API_KEY_AUTHENTICATION)) {
            auditEventLoggingFacade.auditLogFail(RestApiAuditEvent.AUTH_CREDENTIALS_DISCOVERY,
                    event.getCredentialsNotFoundException());
        }
    }
}
