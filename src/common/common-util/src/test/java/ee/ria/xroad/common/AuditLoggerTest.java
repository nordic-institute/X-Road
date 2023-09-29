/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

@Slf4j
public class AuditLoggerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditLogger.class);

    @Before
    public void setUp() {
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @After
    public void tearDown() {
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    public void log() throws JsonProcessingException {
        byte[] tmp = new byte[32];
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = (byte)i;
        }
        AuditLogger.log(
                "event\u008d",
                "test趍\uD801\uDC00user\u008d\r\n\t <" + new String(tmp, StandardCharsets.UTF_8) + ">", "1.1.1.1",
                null);
        assertTrue(appender.list.get(0).getFormattedMessage().chars().allMatch(x -> x > 31 && x < 128));
    }
}
