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
package ee.ria.xroad.common.util.healthcheck;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.proxy.addon.module.HardwareSecurityModuleUtils;
import ee.ria.xroad.proxy.conf.KeyConf;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static ee.ria.xroad.common.util.healthcheck.HealthCheckResult.OK;
import static ee.ria.xroad.common.util.healthcheck.HealthCheckResult.failure;

/**
 * A collection of {@link HealthCheckProvider}s that do not depend on external states (e.g. a thread pool)
 */

@Slf4j
public final class HealthChecks {

    private HealthChecks() {
        //Utility class
    }

    /**
     * A {@link HealthCheckProvider} that checks the authentication key and its OCSP response status
     * @return the result of the check
     */
    public static HealthCheckProvider checkAuthKeyOcspStatus() {

        return () -> {

            // this fails if signer is down
            AuthKey authKey = KeyConf.getAuthKey();

            if (authKey == null) {
                return failure("No authentication key available. Signer might be down.");
            }

            CertChain certChain = authKey.getCertChain();

            if (certChain == null) {
                return failure("No certificate chain available in authentication key.");
            }
            X509Certificate certificate = certChain.getEndEntityCert();

            if (certificate == null) {
                return failure("No end entity certificate available for authentication key.");
            }

            int ocspStatus;

            try {
                ocspStatus = KeyConf.getOcspResponse(certificate).getStatus();
            } catch (Exception e) {
                log.error("Getting OCSP response for authentication key failed, got exception", e);
                return failure("Getting OCSP response for authentication key failed.");
            }

            return new HealthCheckResult(OCSPResp.SUCCESSFUL == ocspStatus,
                    "Authentication key OCSP response status " + ocspStatus);
        };
    }

    /**
     * A {@link HealthCheckProvider} that checks it can access and retrieve data from the {@link ServerConf}
     * (the database behind it).
     * @return the result of the check
     */
    public static HealthCheckProvider checkServerConfDatabaseStatus() {
        return () -> {
            try {
                if (!ServerConf.isAvailable()) {
                    return failure("ServerConf is not available");
                }
                //this fails if the database has not been initialized
                ServerConf.getIdentifier();
            } catch (RuntimeException e) {
                log.error("Got exception while checking server configuration db status", e);
                return failure("Server Conf database did not respond as expected");
            }
            return OK;
        };
    }

    /**
     * A {@link HealthCheckProvider} that verifies {@link GlobalConf} validity
     * @return the result of global conf check
     */
    public static HealthCheckProvider checkGlobalConfStatus() {
        return () -> {
            try {
                GlobalConf.verifyValidity();
                return OK;
            } catch (Exception e) {
                log.error("Exception when verifying global conf validity", e);
                return failure("Global configuration is expired");
            }
        };
    }

    /**
     * A {@link HealthCheckProvider} that check if Hardware Security Modules are operational
     * @return the result of HSM check
     */
    public static HealthCheckProvider checkHSMOperationStatus() {
        return () -> {
            try {
                HardwareSecurityModuleUtils.verifyAllHSMOperational();
                return OK;
            } catch (Exception e) {
                log.error("Exception when verifying HSM status", e);
                return failure("At least one HSM are non operational");
            }
        };
    }

    /**
     * Caches the result from the {@link HealthCheckProvider} for the specified time. You might want to check
     * often if a previously ok system is still ok but check more rarely if a previously
     * broken system is still broken
     * @param resultValidFor the time a successful result is cached
     * @param errorResultValidFor the time an error result is cached
     * @param timeUnit the {@link TimeUnit} for the given times
     * @return
     */
    public static Function<HealthCheckProvider, HealthCheckProvider> cacheResultFor(
            int resultValidFor, int errorResultValidFor, TimeUnit timeUnit) {

        return (healthCheckProvider) -> new HealthCheckProvider() {

            private Supplier<HealthCheckResult> resultSupplier = Suppliers
                    .memoizeWithExpiration(healthCheckProvider::get, resultValidFor, timeUnit);

            boolean previousResult = true;

            @Override
            public HealthCheckResult get() {
                HealthCheckResult result = resultSupplier.get();

                // if the result has changed, switch to the other (longer/shorter) cache if necessary
                if (previousResult != result.isOk()) {
                    resetCacheAndPut(result);
                    previousResult = result.isOk();
                }
                return result;
            }

            private void resetCacheAndPut(HealthCheckResult result) {
                int validityTime = (result.isOk()) ? resultValidFor : errorResultValidFor;

                // recreate the (lightweight) cache, and "put" the value we already have by wrapping it
                // in a decorator that caches that result once.
                final Supplier<HealthCheckResult> supplier = Suppliers
                        .memoizeWithExpiration(cacheResultOnce(healthCheckProvider, result)::get,
                                validityTime, timeUnit);
                // pull the value from the cache into the supplier. Avoids returning an old value if the
                // new supplier is not accessed during the validityTime.
                supplier.get();
                resultSupplier = supplier;
            }
        };
    }

    /**
     * As the name implies, caches the given result once and calls the given provider on subsequent calls.
     * @param provider the provider for {@link HealthCheckResult}s beyond the first result
     * @param cachedOnceResult the first result to return
     * @return a provider wrapping the given provider
     */
    public static HealthCheckProvider cacheResultOnce(HealthCheckProvider provider,
            HealthCheckResult cachedOnceResult) {
        return new HealthCheckProvider() {

            private boolean once = true;

            @Override
            public HealthCheckResult get() {
                if (once) {
                    once = false;
                    return cachedOnceResult;
                }
                return provider.get();
            }
        };
    }
}
