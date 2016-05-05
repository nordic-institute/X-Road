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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.SystemProperties;

/**
 * Encapsulates configuration for async sender.
 */
public class AsyncSenderConf {

    private static final Logger LOG =
            LoggerFactory.getLogger(AsyncSenderConf.class);

    private static final String BASE_DELAY_KEY = "base-delay";
    private static final String MAX_DELAY_KEY = "max-delay";
    private static final String MAX_SENDERS_KEY = "max-senders";

    static final int DEFAULT_BASE_DELAY = 300;
    static final int DEFAULT_MAX_DELAY = 1800;
    static final int DEFAULT_MAX_SENDERS = 1000;

    private final Properties properties;

    /**
     * Creates configuration for sender of asynchronous messages.
     */
    public AsyncSenderConf() {
        properties = new Properties();

        String file = SystemProperties.getAsyncSenderConfFile();
        try (InputStream in = new FileInputStream(file)) {
            load(in);
        } catch (Exception e) {
            LOG.error("Failed to load server configuration file,"
                    + " using default parameters", e);
        }
    }

    /**
     * Base delay
     *
     * @return - base delay for asynchronous messages sender
     */
    public int getBaseDelay() {
        return getInt(BASE_DELAY_KEY, DEFAULT_BASE_DELAY);
    }

    /**
     * Setting base delay
     * @param baseDelay - new base delay for asynchronous messages sender
     */
    public void setBaseDelay(int baseDelay) {
        setInt(BASE_DELAY_KEY, baseDelay);
    }

    /**
     * Max delay
     *
     * @return - max delay for asynchronous messages sender
     */
    public int getMaxDelay() {
        return getInt(MAX_DELAY_KEY, DEFAULT_MAX_DELAY);
    }

    /**
     * Setting max delay
     * @param maxDelay - new base delay for asynchronous messages sender
     */
    public void setMaxDelay(int maxDelay) {
        setInt(MAX_DELAY_KEY, maxDelay);
    }

    /**
     * Max senders
     *
     * @return - max senders for asynchronous messages sender
     */
    public int getMaxSenders() {
        return getInt(MAX_SENDERS_KEY, DEFAULT_MAX_SENDERS);
    }

    /**
     * Setting max senders
     *
     * @param maxSenders - new max senders for asynchronous messages sender
     */
    public void setMaxSenders(int maxSenders) {
        setInt(MAX_SENDERS_KEY, maxSenders);
    }

    void load(InputStream in) throws Exception {
        properties.load(in);
    }

    void save(OutputStream out) throws Exception {
        properties.store(out, null);
    }

    private int getInt(String key, int defaultValue) {
        String value =
                properties.getProperty(key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOG.error(key + " value must be integer (" + value + ")");
            return defaultValue;
        }
    }

    private void setInt(String key, int value) {
        properties.setProperty(key, Integer.toString(value));
    }
}
