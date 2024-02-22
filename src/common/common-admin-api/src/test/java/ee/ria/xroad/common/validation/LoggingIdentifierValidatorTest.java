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
package ee.ria.xroad.common.validation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggingIdentifierValidatorTest {
    public static final String VALID_IDENTIFIER = "aa";
    public static final String INVALID_IDENTIFIER = ":";
    public static final String LEGACY_VALID_IDENTIFIER = "a a";
    private LoggingIdentifierValidator validator;

    private ListAppender<ILoggingEvent> appender;
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingIdentifierValidator.class);


    @Before
    public void setup() {
        validator = new LoggingIdentifierValidator();

        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void teardown() {
        logger.detachAppender(appender);
        appender.stop();
    }


    @Test
    public void noLogMessageForValidIdentifer() {
        validator.isValid(VALID_IDENTIFIER);

        assertThat(appender.list).isEmpty();
    }

    @Test
    public void noLogMessageForInvalidIdentifer() {
        validator.isValid(INVALID_IDENTIFIER);

        assertThat(appender.list).isEmpty();
    }

    @Test
    public void logWarningForLegacyValidIdentifier() {
        validator.isValid(LEGACY_VALID_IDENTIFIER);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

}
