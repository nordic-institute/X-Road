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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Helper for initializing or updating {@link RestApiAuditEvent} associated with current request.
 * Attempts to call update methods from outside of request scope will cause {@link IllegalStateException}
 */
@Component
@Slf4j
@Profile({ "nontest", "audit-test" })
public class AuditEventHelper {

    private final RequestScopedAuditDataHolder requestScopedAuditDataHolder;

    @Autowired
    public AuditEventHelper(RequestScopedAuditDataHolder requestScopedAuditDataHolder) {
        this.requestScopedAuditDataHolder = requestScopedAuditDataHolder;
    }

    /**
     * Set initial {@link RestApiAuditEvent} associated with current request
     * @param event
     * @throws IllegalStateException if initial event has already been set, or not inside request scope
     */
    void initRequestScopedEvent(RestApiAuditEvent event) {
        updateRequestScopedEvent(event, true);
    }

    /**
     * Change {@link RestApiAuditEvent} associated with current request
     * @param event
     * @throws IllegalStateException if initial event has not yet been set, or not inside request scope
     */
    public void changeRequestScopedEvent(RestApiAuditEvent event) {
        updateRequestScopedEvent(event, false);
    }

    /**
     * @param init true = setting first value, exception if old value exist. false = changing value, exception if
     *                old value does not exist
     */
    private void updateRequestScopedEvent(RestApiAuditEvent event, boolean init) {
        RestApiAuditEvent existing = requestScopedAuditDataHolder.getAuditEvent();
        if (init && existing != null) {
            throw new IllegalStateException("request scope already has event " + existing);
        } else if (!init && existing == null) {
            throw new IllegalStateException("request scope did not have event to override");
        } else {
            requestScopedAuditDataHolder.setAuditEvent(event);
        }
    }
}
