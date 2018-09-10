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

import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.util.FileContentChangeChecker;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Test to verify that CachingKeyConf works as expected when it comes to threading
 */
@Slf4j
public class CachingKeyConfImplTest {

    private static final BooleanSupplier ALWAYS_TRUE = () -> true;
    private static final BooleanSupplier ALWAYS_FALSE = () -> false;
    private static final BooleanSupplier CHANGED_KEY_CONF = ALWAYS_TRUE;
    private static final BooleanSupplier UNCHANGED_KEY_CONF = ALWAYS_FALSE;
    private static final BooleanSupplier VALID_AUTH_KEY = ALWAYS_TRUE;
    private static final BooleanSupplier INVALID_AUTH_KEY = ALWAYS_FALSE;

    @Test(timeout = 5000)
    public void testReadsWithChangedKeyConf() throws Exception {
        CachingKeyConfImpl.invalidateCaches();
        AtomicInteger callsToGetAuthKeyInfo = new AtomicInteger(0);
        int expectedCacheHits = 0;
        // first read keys from cache with 5 threads, key conf is not changing
        // should cause 1 cache refresh
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                UNCHANGED_KEY_CONF, VALID_AUTH_KEY, 5, 500);
        expectedCacheHits = expectedCacheHits + 1;
        assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());

        // next read one key, but this time key conf has changed -> one more hit
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                CHANGED_KEY_CONF, VALID_AUTH_KEY, 1, 1);
        expectedCacheHits = expectedCacheHits + 1;
        assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());

        // if we read with 5 threads, and key conf is always changed, what can happen:
        // - all threads check "keyConfHasChanged()" at the same time,
        // and invalidate caches at the same time -> only one extra hit
        // - thread 1 checks "keyConfHasChanged()", reads value and causes extra hit,
        // next thread 2 checks and causes extra hit, ... -> five extra hits
        // - some combination between those two
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                CHANGED_KEY_CONF, VALID_AUTH_KEY, 5, 500);
        int expectedMinimumCacheHits = expectedCacheHits + 1;
        int expectedMaximumCacheHits = expectedCacheHits + 5;
        log.debug("total cache hits: {}", callsToGetAuthKeyInfo.get());
        assertThat(callsToGetAuthKeyInfo.get(), allOf(
                greaterThanOrEqualTo(expectedMinimumCacheHits),
                lessThanOrEqualTo(expectedMaximumCacheHits)));
    }


    @Test(timeout = 5000)
    public void testCachedAuthKeyIsInvalid() throws Exception {
        AtomicInteger callsToGetAuthKeyInfo = new AtomicInteger(0);
        CachingKeyConfImpl.invalidateCaches();
        // read:
        // 1. return valid key normally
        // 2. key becomes invalid -> causes cache refresh
        TogglableBooleanSupplier keyValidity = new TogglableBooleanSupplier(true);
        int expectedCacheHits = 0;
        // first read keys from cache with 5 threads,
        // should cause 1 initial read and 1 cache refresh
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                UNCHANGED_KEY_CONF, keyValidity, 5, 1);
        expectedCacheHits = expectedCacheHits + 1;
        assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());

        // next read one key, but this time key is not valid -> one more hit
        keyValidity.setValue(false);
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                UNCHANGED_KEY_CONF, keyValidity, 1, 1);
        expectedCacheHits = expectedCacheHits + 1;
        assertEquals(expectedCacheHits, callsToGetAuthKeyInfo.get());

        // if we read with 5 threads, and key conf is always invalid, what can happen:
        // - all threads check "info.verifyValidity(new Date())" at the same time,
        // and invalidate cache at the same time -> only one extra hit
        // - thread 1 checks "info.verifyValidity", reads value and causes extra hit,
        // next thread 2 checks and causes extra hit, ... -> five extra hits
        // - some combination between those two
        doConcurrentAuthKeyReads(callsToGetAuthKeyInfo,
                UNCHANGED_KEY_CONF, keyValidity, 5, 500);
        int expectedMinimumCacheHits = expectedCacheHits + 1;
        int expectedMaximumCacheHits = expectedCacheHits + 5;
        log.debug("total cache hits: {}", callsToGetAuthKeyInfo.get());
        assertThat(callsToGetAuthKeyInfo.get(), allOf(
                greaterThanOrEqualTo(expectedMinimumCacheHits),
                lessThanOrEqualTo(expectedMaximumCacheHits)));
    }


    /**
     * Test reads from cache with 1..n threads
     * @param callsToGetAuthKeyInfo counter for cache refreshes with getAuthKeyInfo
     * @param keyConfHasChanged tells if key conf has changed
     * @param authKeyIsValid tells if key is valid (only set for new items added to cache)
     * @param threads how many threads read from cache
     * @param slowCacheReadTimeMs how much cache refresh is slowed
     * @throws Exception
     */
    private void doConcurrentAuthKeyReads(AtomicInteger callsToGetAuthKeyInfo,
                                         BooleanSupplier keyConfHasChanged,
                                         BooleanSupplier authKeyIsValid,
                                         int threads,
                                         int slowCacheReadTimeMs) throws Exception {

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final TestCachingKeyConfImpl testCachingKeyConf = new TestCachingKeyConfImpl(
                callsToGetAuthKeyInfo,
                keyConfHasChanged,
                authKeyIsValid,
                slowCacheReadTimeMs);

        Callable<AuthKey> readKeyFromCache = () -> {
            try {
                return testCachingKeyConf.getAuthKey();
            } catch (Throwable t) {
                log.debug("got error", t);
                throw t;
            }
        };

        List<Callable<AuthKey>> callables = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            callables.add(readKeyFromCache);
        }
        List<Future<AuthKey>> results = executorService.invokeAll(callables);
        for (Future<AuthKey> result: results) {
            result.get();
        }
        executorService.shutdown();
        return;
    }

    /**
     * Test cache implementation that allows for controlling key conf validity,
     * auth key validity, and cache refresh delay
     */
    private class TestCachingKeyConfImpl extends CachingKeyConfImpl {
        final AtomicInteger callsToGetAuthKeyInfo;
        final BooleanSupplier keyConfHasChanged;
        final BooleanSupplier authKeyIsValid;
        final int cacheReadDelayMs;
        TestCachingKeyConfImpl(AtomicInteger callsToGetAuthKeyInfo,
                               BooleanSupplier keyConfHasChanged,
                               BooleanSupplier authKeyIsValid,
                               int cacheReadDelayMs
        ) throws Exception {
            this.callsToGetAuthKeyInfo = callsToGetAuthKeyInfo;
            this.keyConfHasChanged = keyConfHasChanged;
            this.authKeyIsValid = authKeyIsValid;
            this.cacheReadDelayMs = cacheReadDelayMs;
        }

        @Override
        protected FileContentChangeChecker getKeyConfChangeChecker() throws Exception {
            return new FileContentChangeChecker("dummyFileName") {
                @Override
                protected String calculateConfFileChecksum(File file) throws Exception {
                    return "dummyChecksum";
                }

                @Override
                public boolean hasChanged() throws Exception {
                    log.debug("asking if key conf has changed, answer: " + keyConfHasChanged.getAsBoolean());
                    return keyConfHasChanged.getAsBoolean();
                }
            };
        }

        @Override
        protected AuthKeyInfo getAuthKeyInfo() throws Exception {
            callsToGetAuthKeyInfo.incrementAndGet();
            log.debug("simulating a slow read");
            Thread.currentThread().sleep(cacheReadDelayMs);
            return new AuthKeyInfo(null, null, null) {
                @Override
                boolean verifyValidity(Date atDate) {
                    return authKeyIsValid.getAsBoolean();
                }
            };
        }

    }

    /**
     * BooleanSupplier which allows for changing value
     */
    private class TogglableBooleanSupplier implements BooleanSupplier {
        private boolean value;

        public TogglableBooleanSupplier(boolean value) {
            this.value = value;
        }

        public void setValue(boolean value) {
            this.value = value;
        }

        @Override
        public boolean getAsBoolean() {
            return value;
        }
    }

}
