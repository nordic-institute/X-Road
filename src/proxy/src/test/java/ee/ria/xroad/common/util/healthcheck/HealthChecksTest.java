/**
 * The MIT License
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
package ee.ria.xroad.common.util.healthcheck;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.GetHSMOperationalInfo;
import ee.ria.xroad.signer.protocol.message.GetHSMOperationalInfoResponse;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.util.healthcheck.HealthCheckResult.OK;
import static ee.ria.xroad.common.util.healthcheck.HealthCheckResult.failure;
import static ee.ria.xroad.common.util.healthcheck.HealthChecks.cacheResultFor;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HealthChecks}. NB! KeyConf.reload and ServerConf.reload make the test setup brittle as there is no
 * clean way to clear the conf after a test has run. Other tests might be brittle if they expect their conf to
 * be in a certain state before the tests are run (and they don't set the conf up themselves)
 */
public class HealthChecksTest {

    @Test
    public void cacheResultOnceShouldCacheOnce() {

        // prepare
        final HealthCheckResult expectedFirstResult = failure("message for failure");
        final HealthCheckResult expectedSecondResult = failure("some other message");

        final HealthCheckProvider mockProvider = mock(HealthCheckProvider.class);
        when(mockProvider.get()).thenReturn(expectedSecondResult);

        final HealthCheckProvider testedProvider = HealthChecks.cacheResultOnce(mockProvider, expectedFirstResult);

        // execute & verify
        assertEquals("first result does not match", expectedFirstResult, testedProvider.get());
        assertEquals("second result does not match", expectedSecondResult, testedProvider.get());
        // next results should come from the provider, too
        assertEquals("third result does not match", expectedSecondResult, testedProvider.get());
    }

    @Test
    public void cacheResultForShouldCacheSuccessForSetTime() throws InterruptedException {

        // prepare
        final HealthCheckResult expectedFirstResult = OK;
        final HealthCheckResult expectedSecondResult = expectedFirstResult;
        final HealthCheckResult expectedThirdResult = failure("third result");

        final int okCache = 1;
        final int errorCache = 2;

        // Mockito 1.10.19 does not seem to work with interface default methods:
        // stackoverflow.com/questions/27663252/can-you-make-mockito-1-10-17-work-with-default-methods-in-interfaces
        // wrapping this mockProvider with the tested cacheResultFor method returns null
        // final HealthCheckProvider mockProvider = mock(HealthCheckProvider.class);
        // when(mockProvider.get()).thenReturn(expectedFirstResult, expectedThirdResult);

        final HealthCheckProvider mockProvider = mockToReturn(expectedFirstResult, expectedThirdResult);

        final HealthCheckProvider testedProvider = mockProvider.map(
                cacheResultFor(okCache, errorCache, TimeUnit.SECONDS));

        // execute & verify
        assertEquals("first result does not match", expectedFirstResult, testedProvider.get());
        assertEquals("second result does not come from the cache", expectedSecondResult, testedProvider.get());
        Thread.sleep(okCache * 1000L + 500L);
        assertEquals("third result does come from the cache", expectedThirdResult, testedProvider.get());
    }

    @Test
    public void cacheResultForShouldCacheErrorForSetTime() throws InterruptedException {

        // prepare
        final HealthCheckResult expectedFirstResult = failure("first result");
        final HealthCheckResult expectedSecondResult = expectedFirstResult;
        final HealthCheckResult expectedThirdResult = OK;

        final int okCache = 1;
        final int errorCache = 2;

        // Mockito 1.10.19 does not seem to work with interface default methods, see above test method
        final HealthCheckProvider mockProvider = mockToReturn(expectedFirstResult, expectedThirdResult);

        final HealthCheckProvider testedProvider = mockProvider.map(
                cacheResultFor(okCache, errorCache, TimeUnit.SECONDS));

        // execute & verify
        assertEquals("first result does not match", expectedFirstResult, testedProvider.get());
        assertEquals("second result does not come from the cache", expectedSecondResult, testedProvider.get());
        Thread.sleep(errorCache * 1000L + 500L);
        assertEquals("third result does come from the cache", expectedThirdResult, testedProvider.get());
    }


    // this is only necessary until we can use
    // Mockito.when(mock(HealthCheckProvider.class).get()).thenReturn(expectedFirstResult, expectedThirdResult)
    private static HealthCheckProvider mockToReturn(HealthCheckResult... results) {

        Iterator<HealthCheckResult> resultIterator = Arrays.stream(results).iterator();

        return () -> {
            if (resultIterator.hasNext()) {
                return resultIterator.next();
            } else {
                throw new IllegalStateException("Too many requests");
            }
        };
    }

    @Test
    public void checkHSMOperationalShouldReturnOkStatusWhenValid() {

        // prepare
        try (MockedStatic<SignerClient> client = mockStatic(SignerClient.class)) {
            client.when(() -> SignerClient.execute(any(GetHSMOperationalInfo.class)))
                    .thenReturn(new GetHSMOperationalInfoResponse(true));

            // execute
            HealthCheckProvider testedProvider = HealthChecks.checkHSMOperationStatus();

            // verify
            assertTrue("result should be OK", testedProvider.get().isOk());
        }
    }

    @Test
    public void checkHSMOperationalShouldFailWhenNotValid() {

        // prepare
        try (MockedStatic<SignerClient> client = mockStatic(SignerClient.class)) {
            client.when(() -> SignerClient.execute(any(GetHSMOperationalInfo.class)))
                    .thenReturn(new GetHSMOperationalInfoResponse(false));

            // execute
            HealthCheckProvider testedProvider = HealthChecks.checkHSMOperationStatus();
            HealthCheckResult checkedResult = testedProvider.get();

            // verify
            assertFalse("health check result should be a failure", checkedResult.isOk());
            assertThat(checkedResult.getErrorMessage(),
                    containsString("At least one HSM are non operational"));
        }
    }
    @Test
    public void checkGlobalConfShouldReturnOkStatusWhenValid() {

        // prepare
        GlobalConfProvider mockProvider = mock(GlobalConfProvider.class);
        when(mockProvider.isValid()).thenReturn(true);

        GlobalConf.reload(mockProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkGlobalConfStatus();

        // verify
        assertTrue("result should be OK", testedProvider.get().isOk());
    }

    @Test
    public void checkGlobalConfShouldFailWhenNotValid() {

        // prepare
        GlobalConfProvider mockProvider = mock(GlobalConfProvider.class);
        when(mockProvider.isValid()).thenReturn(false);

        GlobalConf.reload(mockProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkGlobalConfStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertFalse("health check result should be a failure", checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(),
                containsString("Global configuration is expired"));
    }

    @Test
    public void checkServerConfShouldReturnOkStatusWhenServerConfReturnsInfo() {

        // prepare
        ServerConfProvider mockProvider = mock(ServerConfProvider.class);
        when(mockProvider.isAvailable()).thenReturn(true);
        when(mockProvider.getIdentifier()).thenReturn(
                SecurityServerId.Conf.create("XE", "member", "code", "servercode"));

        ServerConf.reload(mockProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkServerConfDatabaseStatus();

        // verify
        assertTrue("result should be OK", testedProvider.get().isOk());
    }

    @Test
    public void checkServerConfShouldReturnNotOkWhenServerConfThrows() {

        // prepare
        ServerConfProvider mockProvider = mock(ServerConfProvider.class);
        when(mockProvider.getIdentifier()).thenThrow(new RuntimeException("broken conf!"));

        ServerConf.reload(mockProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkServerConfDatabaseStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(), containsString("ServerConf is not available"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldFailWhenAuthKeyNotAvailable() {

        // prepare
        KeyConfProvider mockKeyConfProvider = mock(KeyConfProvider.class);
        when(mockKeyConfProvider.getAuthKey()).thenReturn(null);

        KeyConf.reload(mockKeyConfProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(), containsString("No authentication key available"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldFailWhenCertChainNotAvailable() {

        // prepare
        AuthKey authKey = new AuthKey(null, null);

        KeyConfProvider mockKeyConfProvider = mock(KeyConfProvider.class);
        when(mockKeyConfProvider.getAuthKey()).thenReturn(authKey);

        KeyConf.reload(mockKeyConfProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(), containsString("No certificate chain available"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldFailWhenEndEntityCertNotAvailable() {

        // prepare
        CertChain mockCertChain = mock(CertChain.class);
        when(mockCertChain.getEndEntityCert()).thenReturn(null);

        AuthKey authKey = new AuthKey(mockCertChain, null);

        KeyConfProvider mockKeyConfProvider = mock(KeyConfProvider.class);
        when(mockKeyConfProvider.getAuthKey()).thenReturn(authKey);

        KeyConf.reload(mockKeyConfProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(), containsString("No end entity certificate available"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldFailWhenOcspStatusNotGood() throws Exception {

        // prepare
        KeyConfProvider mockKeyConfProvider = createMockProviderWithOcspStatus(OCSPResp.INTERNAL_ERROR);

        KeyConf.reload(mockKeyConfProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(),
                containsString("Authentication key OCSP response status"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldBeOkWhenOcspStatusIsGood() throws Exception {

        // prepare
        KeyConfProvider mockKeyConfProvider = createMockProviderWithOcspStatus(OCSPResp.SUCCESSFUL);
        KeyConf.reload(mockKeyConfProvider);

        // execute
        HealthCheckProvider testedProvider = HealthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check should pass", checkedResult.isOk());
    }

    private static KeyConfProvider createMockProviderWithOcspStatus(int status) throws Exception {
        X509Certificate mockCertificate = mock(X509Certificate.class);
        when(mockCertificate.getSubjectX500Principal()).thenReturn(
                new X500Principal("CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));

        CertChain mockCertChain = mock(CertChain.class);
        when(mockCertChain.getEndEntityCert()).thenReturn(mockCertificate);

        AuthKey authKey = new AuthKey(mockCertChain, null);

        KeyConfProvider mockKeyConfProvider = mock(KeyConfProvider.class);
        when(mockKeyConfProvider.getAuthKey()).thenReturn(authKey);

        OCSPResp mockResponse = mock(OCSPResp.class);
        when(mockResponse.getStatus()).thenReturn(status);

        when(mockKeyConfProvider.getOcspResponse((X509Certificate) notNull())).thenReturn(mockResponse);

        return  mockKeyConfProvider;
    }
}
