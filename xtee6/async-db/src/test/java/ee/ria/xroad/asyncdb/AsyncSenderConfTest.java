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
