/*
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
package org.niis.xroad.proxy.core.util.healthcheck;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.proxy.core.auth.AuthKey;
import org.niis.xroad.proxy.core.conf.KeyConfProvider;
import org.niis.xroad.proxy.core.healthcheck.HealthCheckProvider;
import org.niis.xroad.proxy.core.healthcheck.HealthCheckResult;
import org.niis.xroad.proxy.core.healthcheck.HealthChecks;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.signer.client.SignerProxy;

import javax.security.auth.x500.X500Principal;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.healthcheck.HealthCheckResult.OK;
import static org.niis.xroad.proxy.core.healthcheck.HealthCheckResult.failure;

/**
 * Tests for {@link HealthChecks}. NB! KeyConf.reload and ServerConf.reload make the test setup brittle as there is no
 * clean way to clear the conf after a test has run. Other tests might be brittle if they expect their conf to
 * be in a certain state before the tests are run (and they don't set the conf up themselves)
 */
public class HealthChecksTest {

    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final KeyConfProvider keyConfProvider = mock(KeyConfProvider.class);
    private final ServerConfProvider serverConfProvider = mock(ServerConfProvider.class);
    private final HealthChecks healthChecks = new HealthChecks(globalConfProvider, keyConfProvider, serverConfProvider);


    @Test
    public void cacheResultOnceShouldCacheOnce() {

        // prepare
        final HealthCheckResult expectedFirstResult = failure("message for failure");
        final HealthCheckResult expectedSecondResult = failure("some other message");

        final HealthCheckProvider mockProvider = mock(HealthCheckProvider.class);
        when(mockProvider.get()).thenReturn(expectedSecondResult);

        final HealthCheckProvider testedProvider = healthChecks.cacheResultOnce(mockProvider, expectedFirstResult);

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
                healthChecks.cacheResultFor(okCache, errorCache, TimeUnit.SECONDS));

        // execute & verify
        assertEquals("first result does not match", expectedFirstResult, testedProvider.get());
        assertEquals("second result does not come from the cache", expectedFirstResult, testedProvider.get());
        Thread.sleep(okCache * 1000L + 500L);
        assertEquals("third result does come from the cache", expectedThirdResult, testedProvider.get());
    }

    @Test
    public void cacheResultForShouldCacheErrorForSetTime() throws InterruptedException {

        // prepare
        final HealthCheckResult expectedFirstResult = failure("first result");
        final HealthCheckResult expectedThirdResult = OK;

        final int okCache = 1;
        final int errorCache = 2;

        // Mockito 1.10.19 does not seem to work with interface default methods, see above test method
        final HealthCheckProvider mockProvider = mockToReturn(expectedFirstResult, expectedThirdResult);

        final HealthCheckProvider testedProvider = mockProvider.map(
                healthChecks.cacheResultFor(okCache, errorCache, TimeUnit.SECONDS));

        // execute & verify
        assertEquals("first result does not match", expectedFirstResult, testedProvider.get());
        assertEquals("second result does not come from the cache", expectedFirstResult, testedProvider.get());
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
        try (MockedStatic<SignerProxy> client = mockStatic(SignerProxy.class)) {
            client.when(SignerProxy::isHSMOperational).thenReturn(true);

            // execute
            HealthCheckProvider testedProvider = healthChecks.checkHSMOperationStatus();

            // verify
            assertTrue("result should be OK", testedProvider.get().isOk());
        }
    }

    @Test
    public void checkHSMOperationalShouldFailWhenNotValid() {

        // prepare
        try (MockedStatic<SignerProxy> client = mockStatic(SignerProxy.class)) {
            client.when(SignerProxy::isHSMOperational).thenReturn(false);

            // execute
            HealthCheckProvider testedProvider = healthChecks.checkHSMOperationStatus();
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
        when(globalConfProvider.isValid()).thenReturn(true);

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkGlobalConfStatus();

        // verify
        assertTrue("result should be OK", testedProvider.get().isOk());
    }

    @Test
    public void checkGlobalConfShouldFailWhenNotValid() {

        // prepare
        doThrow(new RuntimeException("Global configuration is expired")).when(globalConfProvider).verifyValidity();

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkGlobalConfStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertFalse("health check result should be a failure", checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(),
                containsString("Global configuration is expired"));
    }

    @Test
    public void checkServerConfShouldReturnOkStatusWhenServerConfReturnsInfo() {

        // prepare
        when(serverConfProvider.isAvailable()).thenReturn(true);
        when(serverConfProvider.getIdentifier()).thenReturn(
                SecurityServerId.Conf.create("XE", "member", "code", "servercode"));

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkServerConfDatabaseStatus();

        // verify
        assertTrue("result should be OK", testedProvider.get().isOk());
    }

    @Test
    public void checkServerConfShouldReturnNotOkWhenServerConfThrows() {

        // prepare
        when(serverConfProvider.getIdentifier()).thenThrow(new RuntimeException("broken conf!"));

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkServerConfDatabaseStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(), containsString("ServerConf is not available"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldFailWhenAuthKeyNotAvailable() {

        // prepare
        when(keyConfProvider.getAuthKey()).thenReturn(null);

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(), containsString("No authentication key available"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldFailWhenCertChainNotAvailable() {

        // prepare
        AuthKey authKey = new AuthKey(null, null);

        when(keyConfProvider.getAuthKey()).thenReturn(authKey);

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkAuthKeyOcspStatus();
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

        when(keyConfProvider.getAuthKey()).thenReturn(authKey);

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(), containsString("No end entity certificate available"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldFailWhenOcspStatusNotGood() throws Exception {

        // prepare
        createMockProviderWithOcspStatus(OCSPResp.INTERNAL_ERROR);

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check result should be a failure", !checkedResult.isOk());
        assertThat(checkedResult.getErrorMessage(),
                containsString("Authentication key OCSP response status"));
    }

    @Test
    public void checkAuthKeyOcspStatusShouldBeOkWhenOcspStatusIsGood() throws Exception {

        // prepare
        createMockProviderWithOcspStatus(OCSPResp.SUCCESSFUL);

        // execute
        HealthCheckProvider testedProvider = healthChecks.checkAuthKeyOcspStatus();
        HealthCheckResult checkedResult = testedProvider.get();

        // verify
        assertTrue("health check should pass", checkedResult.isOk());
    }

    private void createMockProviderWithOcspStatus(int status) throws Exception {
        X509Certificate mockCertificate = mock(X509Certificate.class);
        when(mockCertificate.getSubjectX500Principal()).thenReturn(
                new X500Principal("CN=Duke, OU=JavaSoft, O=Sun Microsystems, C=US"));

        CertChain mockCertChain = mock(CertChain.class);
        when(mockCertChain.getEndEntityCert()).thenReturn(mockCertificate);

        AuthKey authKey = new AuthKey(mockCertChain, null);

        when(keyConfProvider.getAuthKey()).thenReturn(authKey);

        OCSPResp mockResponse = mock(OCSPResp.class);
        when(mockResponse.getStatus()).thenReturn(status);

        when(keyConfProvider.getOcspResponse((X509Certificate) notNull())).thenReturn(mockResponse);
    }
}
