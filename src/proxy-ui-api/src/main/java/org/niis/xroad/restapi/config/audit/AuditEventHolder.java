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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.web.context.WebApplicationContext.SCOPE_REQUEST;

@Getter
@Setter
@Component
@Scope(SCOPE_REQUEST)
public class AuditEventHolder {

    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);
    private String eventName;
    private Map<String, Object> eventData = new HashMap<>();

    private int instanceNumber;

    public AuditEventHolder() {
        instanceNumber = INSTANCE_COUNTER.incrementAndGet();
    }

    public void addData(String propertyName, Object value) {
        eventData.put(propertyName, value);
    }

    private static final String PREFIX = "*** audit log *** ";

    public void auditLog(String s) {
        AuditLogger.log(PREFIX + 1 + s + new Date());
        AuditLogger.log(PREFIX + 2 + getDatedEventName(), getEventData());
        AuditLogger.log(PREFIX + (2 + 1) + getEventName(), getEventData());
    }

    public void auditLogFail(String s) {
        AuditLogger.log(PREFIX + 1 +  s + new Date());
        AuditLogger.log(PREFIX + 2 + getDatedEventName(), getEventData());
        AuditLogger.log(PREFIX + (2 + 1) + getEventName() + " failed!", getEventData());
    }

    public String getDatedEventName() {
        return getEventName() + " " + new Date();
    }

}
