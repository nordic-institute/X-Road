package ee.cyber.sdsb.asyncdb;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;

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

    public void save() throws Exception {
        try (OutputStream out = new FileOutputStream(
                SystemProperties.getAsyncSenderConfFile())) {
            save(out);
        }
    }

    public int getBaseDelay() {
        return getInt(BASE_DELAY_KEY, DEFAULT_BASE_DELAY);
    }

    public void setBaseDelay(int baseDelay) {
        setInt(BASE_DELAY_KEY, baseDelay);
    }

    public int getMaxDelay() {
        return getInt(MAX_DELAY_KEY, DEFAULT_MAX_DELAY);
    }

    public void setMaxDelay(int maxDelay) {
        setInt(MAX_DELAY_KEY, maxDelay);
    }

    public int getMaxSenders() {
        return getInt(MAX_SENDERS_KEY, DEFAULT_MAX_SENDERS);
    }

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
