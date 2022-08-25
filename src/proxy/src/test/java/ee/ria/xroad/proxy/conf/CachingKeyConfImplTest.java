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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.FileContentChangeChecker;
import ee.ria.xroad.common.util.filewatcher.FileWatcherRunner;
import ee.ria.xroad.proxy.testsuite.EmptyServerConf;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertEquals;

/**
 * Test to verify that CachingKeyConf works as expected when it comes to threading
 */
@Slf4j
public class CachingKeyConfImplTest {

    // booleanSuppliers for different uses
    // some duplicates for more readability
    private static final BooleanSupplier ALWAYS_TRUE = () -> true;
    private static final BooleanSupplier ALWAYS_FALSE = () -> false;
    private static final BooleanSupplier CHANGED_KEY_CONF = ALWAYS_TRUE;
    private static final BooleanSupplier UNCHANGED_KEY_CONF = ALWAYS_FALSE;
    private static final BooleanSupplier VALID_AUTH_KEY = ALWAYS_TRUE;
    private static final BooleanSupplier INVALID_AUTH_KEY = ALWAYS_FALSE;
    private static final BooleanSupplier VALID_SIGNING_INFO = ALWAYS_TRUE;
    public static final int NO_LOOPING = 1;
    public static final int NO_DELAY = 0;
    private static final Path KEY_CONF = Paths.get("build", "tmp", "keyConf.xml");

    @Before
    public void before() throws IOException {
        System.setProperty(SystemProperties.CONF_PATH, "build/tmp/");
        GlobalConf.reload(new EmptyGlobalConf() {
            @Override
            public String getInstanceIdentifier() {
                return "TEST";
            }
        });
        ServerConf.reload(new EmptyServerConf() {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                return SecurityServerId.Conf.create("TEST", "CLASS", "CODE", "SERVER");
            }
        });
        Files.deleteIfExists(KEY_CONF);
        Files.createFile(KEY_CONF);
    }

    @After
    public void after() throws Exception {
        Files.deleteIfExists(KEY_CONF);
    }

    @Test(timeout = 5000)
    public void testSigningInfoReads() throws Exception {
        AtomicInteger callsToGetInfo = new AtomicInteger(0);
        ClientId client1 = ClientId.Conf.create("FI", "GOV", "1");
        ClientId client2 = ClientId.Conf.create("FI", "GOV", "1", "SS");
        ClientId client3 = ClientId.Conf.create("FI", "GOV", "2");
        List<ClientId> clients = Arrays.asList(client1, client2, client3);
        int expectedCacheHits = 0;
        // first read keys from cache with 5 threads, key conf is not changing
        // should cause 3 cache refreshes (1 per client)
        doConcurrentSigningInfoReads(callsToGetInfo, clients,
                UNCHANGED_KEY_CONF, VALID_AUTH_KEY, VALID_SIGNING_INFO, 5, 5, NO_DELAY);
        expectedCacheHits = expectedCacheHits + 3;
        assertEquals(expectedCacheHits, callsToGetInfo.get());

        // read cached data like in previous step, but one item becomes invalid suddenly -> one extra hit
        BooleanSupplier suddenlyInvalid = new BooleanSupplier() {
            AtomicInteger counter = new AtomicInteger(0);

            @Override
            public boolean getAsBoolean() {
                int number = counter.getAndIncrement();
                if (number == 10) {
                    return false;
                } else {
                    return true;
                }
            }
        };
        doConcurrentSigningInfoReads(callsToGetInfo, clients,
                UNCHANGED_KEY_CONF, VALID_AUTH_KEY, suddenlyInvalid, 5, 5, NO_DELAY);
        expectedCacheHits = expectedCacheHits + 4;
        assertEquals(expectedCacheHits, callsToGetInfo.get());

        // if we read with 5 threads, and key conf is always changed, what can happen:
        // - all threads check "keyConfHasChanged()" at the same time,
        // and invalidate caches at the same time -> only one extra hit
        // - thread 1 checks "keyConfHasChanged()", reads value and causes extra hit,
        // next thread 2 checks and causes extra hit, ... -> five extra hits
        // - some combination between those two
        doConcurrentAuthKeyReads(callsToGetInfo,
                CHANGED_KEY_CONF, VALID_AUTH_KEY, VALID_SIGNING_INFO, 5, NO_LOOPING, 100);
        int expectedMinimumCacheHits = expectedCacheHits + 1;
        int expectedMaximumCacheHits = expectedCacheHits + 5;
        assertThat(callsToGetInfo.get(), allOf(
                greaterThanOrEqualTo(expectedMinimumCacheHits),
                lessThanOrEqualTo(expectedMaximumCacheHits)));
        log.debug("total cache hits: {}", callsToGetInfo.get());
    }

    @Test(timeout = 15000)
    public void testAuthKeyReadsWithChangedKeyConf() throws Exception {

        AtomicInteger callsToGetAuthKeyInfo = new AtomicInteger(0);
        ToggleableBooleanSupplier keyConfHasChanged = new ToggleableBooleanSupplier(false);

        final TestCachingKeyConfImpl testCachingKeyConf = new TestCachingKeyConfImpl(
                callsToGetAuthKeyInfo,
                keyConfHasChanged,
                VALID_AUTH_KEY,
                VALID_SIGNING_INFO,
                NO_DELAY);
        try (FileWatcherRunner unused = CachingKeyConfImpl.createChangeWatcher(
                new WeakReference<>(testCachingKeyConf),
                new TestChangeChecker(keyConfHasChanged))) {

            testCachingKeyConf.ready.await();

            int expectedCacheHits = 1;
            // should cause 1 cache refresh
            testCachingKeyConf.getAuthKey();
            assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());

            keyConfHasChanged.setValue(true);
            // change keyconf
            Files.write(KEY_CONF, "test".getBytes());
            // wait for change to propagate
            // next read one key, but this time key conf has changed -> one more hit
            testCachingKeyConf.changed.await();
            testCachingKeyConf.getAuthKey();

            expectedCacheHits++;
            assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());
        }
    }

    @Test(timeout = 5000)
    public void testCachedAuthKeyIsInvalid() throws Exception {
        AtomicInteger callsToGetAuthKeyInfo = new AtomicInteger(0);
        // read:
        // 1. return valid key normally
        // 2. key becomes invalid -> causes cache refresh
        ToggleableBooleanSupplier keyValidity = new ToggleableBooleanSupplier(true);
        int expectedCacheHits = 0;
        // first read keys from cache with 5 threads,
        // should cause 1 initial read and 1 cache refresh
        final TestCachingKeyConfImpl testCachingKeyConf = new TestCachingKeyConfImpl(
                callsToGetAuthKeyInfo,
                UNCHANGED_KEY_CONF,
                keyValidity,
                VALID_SIGNING_INFO,
                NO_DELAY);

        CacheReadOperation readOperation = new CacheReadOperation(testCachingKeyConf) {
            @Override
            Object readFromCache(Object key) {
                return testCachingKeyConf.getAuthKey();
            }
        };
        doConcurrentCacheReads(readOperation, 5, NO_LOOPING);

        assertEquals(++expectedCacheHits, callsToGetAuthKeyInfo.get());

        // next read one key, but this time key is not valid -> one more hit
        keyValidity.setValue(false);

        doConcurrentCacheReads(readOperation, 1, NO_LOOPING);

        assertEquals(++expectedCacheHits, callsToGetAuthKeyInfo.get());

        // if we read with 5 threads, and key conf is always invalid, what can happen:
        // - all threads check "info.verifyValidity(new Date())" at the same time,
        // and invalidate cache at the same time -> only one extra hit
        // - thread 1 checks "info.verifyValidity", reads value and causes extra hit,
        // next thread 2 checks and causes extra hit, ... -> five extra hits
        // - some combination between those two
        doConcurrentCacheReads(readOperation, 5, NO_LOOPING);

        int expectedMinimumCacheHits = expectedCacheHits + 1;
        int expectedMaximumCacheHits = expectedCacheHits + 5;
        log.debug("total cache hits: {}", callsToGetAuthKeyInfo.get());
        assertThat(callsToGetAuthKeyInfo.get(), allOf(
                greaterThanOrEqualTo(expectedMinimumCacheHits),
                lessThanOrEqualTo(expectedMaximumCacheHits)));
        testCachingKeyConf.destroy();
    }

    @Test(timeout = 5000)
    public void testAuthKeyReadsWithChangedServerId() throws Exception {
        AtomicInteger callsToGetAuthKeyInfo = new AtomicInteger(0);
        int expectedCacheHits = 0;
        // first read keys from cache with 5 threads, server id is not changing
        // should cause 1 cache refresh
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                UNCHANGED_KEY_CONF, VALID_AUTH_KEY, VALID_SIGNING_INFO, 5, NO_LOOPING, 100);
        expectedCacheHits++;
        assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());

        // next read key twice, but this time serverId has changed -> one more hit
        ServerConf.reload(new EmptyServerConf() {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                return SecurityServerId.Conf.create("TEST", "CLASS", "CODE2", "SERVER");
            }
        });
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                UNCHANGED_KEY_CONF, VALID_AUTH_KEY, VALID_SIGNING_INFO, 1, 2, NO_DELAY);
        expectedCacheHits++;
        assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());
    }

    @Test
    public void testCalculateNotAfter() throws Exception {
        final X509Certificate ca = TestCertUtil.getCaCert();
        final TestCertUtil.PKCS12 consumer = TestCertUtil.getConsumer();
        final TestCertUtil.PKCS12 ocsp = TestCertUtil.getOcspSigner();

        final Instant now = Instant.parse("2022-01-01T00:00:00Z");
        final Date expected = Date.from(now.plusSeconds(1800).truncatedTo(SECONDS));
        final OCSPResp response = OcspTestUtils.createOCSPResponse(
                consumer.certChain[0],
                ca,
                ocsp.certChain[0],
                ocsp.key,
                CertificateStatus.GOOD,
                Date.from(now.minusSeconds(1000)),
                expected);

        assertEquals(expected,
                CachingKeyConfImpl.calculateNotAfter(Collections.singletonList(response), ca.getNotAfter()));
    }

    /**
     * Operation that reads from the cache
     */
    private abstract static class CacheReadOperation {
        private CachingKeyConfImpl cache;

        CacheReadOperation(CachingKeyConfImpl cache) {
            this.cache = cache;
        }

        abstract Object readFromCache(Object key) throws Exception;
    }

    /**
     * Test signing info reads from cache concurrently with 1..n threads
     * @param dataRefreshes counter for cache refreshes
     * @param keyConfHasChanged tells if key conf has changed
     * @param authKeyIsValid tells if key is valid (only set for new items added to cache)
     * @param signingInfoIsValid tells if signing info is valid
     * @param concurrentThreads how many threads read from cache
     * @param loops how many times each thread does its thing, on average
     * @param slowCacheReadTimeMs how much cache refresh is slowed
     */
    private void doConcurrentSigningInfoReads(AtomicInteger dataRefreshes,
            List<ClientId> clients,
            BooleanSupplier keyConfHasChanged,
            BooleanSupplier authKeyIsValid,
            BooleanSupplier signingInfoIsValid,
            int concurrentThreads,
            int loops,
            int slowCacheReadTimeMs) throws Exception {

        final TestCachingKeyConfImpl testCachingKeyConf = new TestCachingKeyConfImpl(
                dataRefreshes,
                keyConfHasChanged,
                authKeyIsValid,
                signingInfoIsValid,
                slowCacheReadTimeMs);

        AtomicInteger clientIndex = new AtomicInteger(0);
        CacheReadOperation readOperation = new CacheReadOperation(testCachingKeyConf) {
            @Override
            Object readFromCache(Object key) throws Exception {
                // do a read for each client, iterating the collection
                int index = clientIndex.getAndAdd(1);
                int realIndex = index % clients.size();
                ClientId id = clients.get(realIndex);
                log.debug("reading for client #{} [{}]", realIndex, id);
                return testCachingKeyConf.getSigningCtx(id);
            }
        };
        doConcurrentCacheReads(readOperation, concurrentThreads, loops);
        testCachingKeyConf.destroy();
    }

    /**
     * Test auth key reads from cache concurrently with 1..n threads
     * @param dataRefreshes counter for cache refreshes
     * @param keyConfHasChanged tells if key conf has changed
     * @param authKeyIsValid tells if key is valid (only set for new items added to cache)
     * @param signingInfoIsValid tells if signing info is valid
     * @param concurrentThreads how many threads read from cache
     * @param loops how many times each thread does its thing, on average
     * @param slowCacheReadTimeMs how much cache refresh is slowed
     */
    private void doConcurrentAuthKeyReads(AtomicInteger dataRefreshes,
            BooleanSupplier keyConfHasChanged,
            BooleanSupplier authKeyIsValid,
            BooleanSupplier signingInfoIsValid,
            int concurrentThreads,
            int loops,
            int slowCacheReadTimeMs) throws Exception {

        TestCachingKeyConfImpl testCachingKeyConf = new TestCachingKeyConfImpl(
                dataRefreshes,
                keyConfHasChanged,
                authKeyIsValid,
                signingInfoIsValid,
                slowCacheReadTimeMs);

        CacheReadOperation readOperation = new CacheReadOperation(testCachingKeyConf) {
            @Override
            Object readFromCache(Object key) {
                return testCachingKeyConf.getAuthKey();
            }
        };
        doConcurrentCacheReads(readOperation, concurrentThreads, loops);
        testCachingKeyConf.destroy();
    }

    private void doConcurrentCacheReads(CacheReadOperation readOperation,
            int threads,
            int loops) throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        Callable<Object> readKeyFromCache = () -> {
            try {
                return readOperation.readFromCache(null);
            } catch (Throwable t) {
                log.debug("got error", t);
                throw t;
            }
        };

        List<Callable<Object>> callables = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            for (int j = 0; j < loops; j++) {
                callables.add(readKeyFromCache);
            }
        }
        List<Future<Object>> results = executorService.invokeAll(callables);
        for (Future<Object> result : results) {
            result.get();
        }
        executorService.shutdown();
        return;
    }

    private static class TestChangeChecker extends FileContentChangeChecker {
        private BooleanSupplier keyConfHasChanged;

        TestChangeChecker(BooleanSupplier keyConfHasChanged) throws Exception {
            super(KEY_CONF.toString());
            this.keyConfHasChanged = keyConfHasChanged;
        }

        @Override
        protected String calculateConfFileChecksum(File file) {
            return "dummyChecksum";
        }

        @Override
        public boolean hasChanged() {
            log.debug("asking if key conf has changed, answer: " + keyConfHasChanged.getAsBoolean());
            return keyConfHasChanged.getAsBoolean();
        }
    }

    /**
     * Test cache implementation that allows for controlling key conf validity,
     * auth key validity, and cache refresh delay
     */
    private static class TestCachingKeyConfImpl extends CachingKeyConfImpl {
        final AtomicInteger dataRefreshes;
        final BooleanSupplier keyConfHasChanged;
        final BooleanSupplier authKeyIsValid;
        final BooleanSupplier signingInfoIsValid;
        final int cacheReadDelayMs;
        final CountDownLatch changed = new CountDownLatch(1);
        final CountDownLatch ready = new CountDownLatch(1);

        TestCachingKeyConfImpl(AtomicInteger dataRefreshes,
                BooleanSupplier keyConfHasChanged,
                BooleanSupplier authKeyIsValid,
                BooleanSupplier signingInfoIsValid,
                int cacheReadDelayMs) {
            this.dataRefreshes = dataRefreshes;
            this.keyConfHasChanged = keyConfHasChanged;
            this.authKeyIsValid = authKeyIsValid;
            this.signingInfoIsValid = signingInfoIsValid;
            this.cacheReadDelayMs = cacheReadDelayMs;
        }

        @Override
        protected void watcherStarted() {
            ready.countDown();
        }

        @Override
        protected void invalidateCaches() {
            super.invalidateCaches();
            changed.countDown();
        }

        private void delay(long delayMs) throws Exception {
            if (cacheReadDelayMs > 0) {
                log.debug("simulating a slow read");
                Thread.currentThread().sleep(delayMs);
            }
        }

        @Override
        protected AuthKeyInfo getAuthKeyInfo(SecurityServerId serverId) throws Exception {
            dataRefreshes.incrementAndGet();
            delay(cacheReadDelayMs);

            return new AuthKeyInfo(null, null, null, null) {
                @Override
                boolean verifyValidity(Date atDate) {
                    return authKeyIsValid.getAsBoolean();
                }
            };
        }

        @Override
        protected SigningInfo getSigningInfo(ClientId clientId) throws Exception {
            dataRefreshes.incrementAndGet();
            delay(cacheReadDelayMs);
            return new SigningInfo("keyid", "signmechanismname", null, null, null, null) {
                @Override
                boolean verifyValidity(Date atDate) {
                    return signingInfoIsValid.getAsBoolean();
                }
            };
        }
    }

    /**
     * BooleanSupplier which allows for changing value
     */
    private static class ToggleableBooleanSupplier implements BooleanSupplier {
        private boolean value;

        ToggleableBooleanSupplier(boolean value) {
            this.value = value;
        }

        public void setValue(boolean booleanValue) {
            this.value = booleanValue;
        }

        @Override
        public boolean getAsBoolean() {
            return value;
        }
    }

}
