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

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static ee.ria.xroad.common.util.healthcheck.HealthCheckResult.OK;
import static ee.ria.xroad.common.util.healthcheck.HealthCheckResult.failure;
import static ee.ria.xroad.common.util.healthcheck.HealthChecks.cacheResultFor;
import static ee.ria.xroad.common.util.healthcheck.HealthChecks.checkAuthKeyOcspStatus;
import static ee.ria.xroad.common.util.healthcheck.HealthChecks.checkGlobalConfStatus;
import static ee.ria.xroad.common.util.healthcheck.HealthChecks.checkHSMOperationStatus;
import static ee.ria.xroad.common.util.healthcheck.HealthChecks.checkServerConfDatabaseStatus;

/**
 * A {@link HealthCheckProvider} that does a bunch of health tests and gives an error if any of them fail
 */
@Slf4j
public class StoppableCombinationHealthCheckProvider implements StoppableHealthCheckProvider {

    private final ExecutorService executorService;
    private final List<HealthCheckProvider> healthCheckProviders;

    /**
     * Create a new provider.
     */
    public StoppableCombinationHealthCheckProvider() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.healthCheckProviders = createProviderList();

    }

    @Override
    public HealthCheckResult get() {

        return healthCheckProviders.stream()
                .map(HealthCheckProvider::get)
                .filter(result -> !result.isOk())
                .findFirst().orElse(OK);

    }

    private List<HealthCheckProvider> createProviderList() {

        final int timeout = 5;
        final int resultValidFor = 2;
        final int errorValidFor = 30;

        List<HealthCheckProvider> providers = new ArrayList<>();

        if (SystemProperties.isHSMHealthCheckEnabled()) {
            providers.add(checkHSMOperationStatus()
                    .map(withTimeout(timeout, TimeUnit.SECONDS, "Hardware Security Modules status"))
                    .map(cacheResultFor(resultValidFor, errorValidFor, TimeUnit.SECONDS)));
        }

        providers.add(checkGlobalConfStatus()
                .map(withTimeout(timeout, TimeUnit.SECONDS, "Global conf validity"))
                .map(cacheResultFor(resultValidFor, errorValidFor, TimeUnit.SECONDS)));
        providers.add(checkAuthKeyOcspStatus()
                .map(withTimeout(timeout, TimeUnit.SECONDS, "Authentication key OCSP status"))
                .map(cacheResultFor(resultValidFor, errorValidFor, TimeUnit.SECONDS)));
        providers.add(checkServerConfDatabaseStatus()
                .map(withTimeout(timeout, TimeUnit.SECONDS, "Server conf database status"))
                .map(cacheResultFor(resultValidFor, errorValidFor, TimeUnit.SECONDS)));

        return Collections.unmodifiableList(providers);
    }

    private Function<HealthCheckProvider, HealthCheckProvider> withTimeout(
            int timeoutAfter, TimeUnit timeUnit, String healthCheckNameForErrorReporting) {

        return (healthCheckProvider) -> () -> {
            Future<HealthCheckResult> future = executorService.submit(healthCheckProvider::get);
            try {
                return future.get(timeoutAfter, timeUnit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String msg = String.format("Fetching health check response was interrupted for for: %s",
                        healthCheckNameForErrorReporting);
                log.info(msg);
                future.cancel(true);
                return failure(msg);

            } catch (ExecutionException e) {
                final String msg = String.format("Fetching health check response failed for: %s",
                        healthCheckNameForErrorReporting);
                log.error(msg, e);
                return failure(msg);

            } catch (TimeoutException e) {

                future.cancel(true);
                final String msg =
                        String.format("Fetching health check response timed out for: %s",
                                healthCheckNameForErrorReporting);
                log.error(msg);
                return failure(msg);
            }
        };
    }


    @Override
    public void stop() {
        log.info("Shutting down health check executor service");
        executorService.shutdown();
        try {
            final int timeout = 5;
            executorService.awaitTermination(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Got interrupted while waiting for executor service to shut down");
        } finally {
            executorService.shutdownNow();
            log.info("Finished shutting down Health check executor service");
        }
    }
}
