package ee.cyber.sdsb.asyncdb;

import org.junit.Before;
import org.junit.Test;

import ee.cyber.sdsb.common.SystemProperties;

import static org.junit.Assert.assertEquals;

public class AsyncSenderConfTest {

    @Before
    public void setUp() throws Exception {
        System.setProperty(SystemProperties.SERVER_CONFIGURATION_FILE,
                "src/test/resources/serverconf.xml");
    }

    @Test
    public void readSenderConf() {
        AsyncSenderConf conf = AsyncSenderConf.getInstance();
        assertEquals(300, conf.getBaseDelay());
        assertEquals(1800, conf.getMaxDelay());
        assertEquals(999, conf.getMaxSenders());
    }

    @Test
    public void readDefaultParametersIfServerConfMalformed() {
        System.setProperty(SystemProperties.SERVER_CONFIGURATION_FILE,
                "src/test/resources/serverconf_MALFORMED.xml");

        AsyncSenderConf conf = AsyncSenderConf.getInstance();
        assertEquals(300, conf.getBaseDelay());
        assertEquals(1800, conf.getMaxDelay());
        assertEquals(1000, conf.getMaxSenders());
    }

    @Test
    public void readDefaultParametersIfServerConfFileNotFound() {
        System.setProperty(SystemProperties.SERVER_CONFIGURATION_FILE,
                "NOTHING.xml");

        AsyncSenderConf conf = AsyncSenderConf.getInstance();
        assertEquals(300, conf.getBaseDelay());
        assertEquals(1800, conf.getMaxDelay());
        assertEquals(1000, conf.getMaxSenders());
    }
}
