/**
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
package ee.ria.xroad.common.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class LogMsgConverterTest {

    private Logger testLogger;
    private ByteArrayOutputStream testLogOutput;

    @Before
    public void setUp() {
        testLogger = (Logger) LoggerFactory.getLogger("LogMsgConverterTest");

        final PatternLayout layout = new PatternLayout();
        layout.setPattern("%escape(%msg%n%ex) %n%nopex");
        layout.getDefaultConverterMap().put("escape", LogMsgConverter.class.getName());
        layout.setContext(testLogger.getLoggerContext());

        final LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setLayout(layout);
        encoder.setContext(testLogger.getLoggerContext());

        final OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setName("TestAppender");
        appender.setEncoder(encoder);
        testLogOutput = new ByteArrayOutputStream();
        appender.setOutputStream(testLogOutput);
        appender.setContext(testLogger.getLoggerContext());

        testLogger.setAdditive(false);
        testLogger.addAppender(appender);

        layout.start();
        encoder.start();
        appender.start();
    }

    @After
    public void tearDown() {
        testLogger.detachAndStopAllAppenders();
    }

    @Test
    public void testMessageConverter() throws UnsupportedEncodingException {
        testLogger.info("This is some text\r\n\r\n趍\uD801\uDC00",
                new Exception("\u008dBad message", new Exception("Bad\u008dcause")));
        assertTrue(testLogOutput.toString(StandardCharsets.UTF_8.name()).chars()
                .allMatch(ch -> (ch == '\n' || ch == '\t' || (ch > 31 && ch < 128))));
    }
}
