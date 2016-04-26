/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.asyncdb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.Test;

import ee.ria.xroad.common.SystemProperties;

import static org.junit.Assert.assertEquals;

/**
 * Tests configuration of async sender.
 */
public class AsyncSenderConfTest {

    /**
     * Test loading of async sender conf
     */
    @Test
    public void loadSenderConf() {
        System.setProperty(SystemProperties.ASYNC_SENDER_CONFIGURATION_FILE,
                "src/test/resources/async-sender.properties");

        AsyncSenderConf conf = new AsyncSenderConf();
        assertEquals(300, conf.getBaseDelay());
        assertEquals(1800, conf.getMaxDelay());
        assertEquals(999, conf.getMaxSenders());
    }

    /**
     * Tests whether default values are used when configuration file does not
     * exist.
     */
    @Test
    public void loadSenderConfFileNotExist() {
        System.setProperty(SystemProperties.ASYNC_SENDER_CONFIGURATION_FILE,
                "src/test/resources/XXXasync-sender.properties");

        AsyncSenderConf conf = new AsyncSenderConf();
        assertEquals(AsyncSenderConf.DEFAULT_BASE_DELAY, conf.getBaseDelay());
        assertEquals(AsyncSenderConf.DEFAULT_MAX_DELAY, conf.getMaxDelay());
        assertEquals(AsyncSenderConf.DEFAULT_MAX_SENDERS, conf.getMaxSenders());
    }

    /**
     * Tests load-save cycle of async sender conf.
     *
     * @throws Exception - when either loading or saving of conf fails.
     */
    @Test
    public void loadSaveConf() throws Exception {
        System.setProperty(SystemProperties.ASYNC_SENDER_CONFIGURATION_FILE,
                "src/test/resources/async-sender.properties");

        AsyncSenderConf conf = new AsyncSenderConf();
        assertEquals(300, conf.getBaseDelay());
        assertEquals(1800, conf.getMaxDelay());
        assertEquals(999, conf.getMaxSenders());

        conf.setBaseDelay(554);
        conf.setMaxDelay(445);
        conf.setMaxSenders(444);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        conf.save(out);
        conf.load(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(554, conf.getBaseDelay());
        assertEquals(445, conf.getMaxDelay());
        assertEquals(444, conf.getMaxSenders());
    }
}
