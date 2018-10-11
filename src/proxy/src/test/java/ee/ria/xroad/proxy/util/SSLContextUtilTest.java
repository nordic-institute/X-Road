package ee.ria.xroad.proxy.util;

import ee.ria.xroad.common.SystemProperties;

import org.junit.Test;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;

/**
 * Test for SSLContextUtil
 */
public class SSLContextUtilTest {

    /**
     * Test that created SSLContext supports X-Road accepted cipher suites
     */
    @Test
    public void xroadAcceptedCipherSuites() throws NoSuchAlgorithmException, KeyManagementException {
        String[] supported = SSLContextUtil.createXroadSSLContext().createSSLEngine().getSupportedCipherSuites();
        String[] accepted = SystemProperties.getXroadTLSCipherSuites();
        assertThat(Arrays.asList(supported), hasItems(accepted));
    }
}
