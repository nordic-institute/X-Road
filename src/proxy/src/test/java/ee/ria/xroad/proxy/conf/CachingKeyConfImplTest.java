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

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Test to verify that CachingKeyConf works as expected when it comes to threading
 */
public class CachingKeyConfImplTest {
    @Test(timeout = 5000)
    public void testConcurrentReads() throws Exception {
        // set up 5 threads that concurrently try to refresh cached key
        // make sure just 1 thread actually fetched the key
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch allThreadsAreWaitingForKey = new CountDownLatch(5);
        AtomicInteger callsToGetAuthKeyInfo = new AtomicInteger(0);
        CachingKeyConfImpl testCachingKeyConf = new CachingKeyConfImpl() {
            @Override
            protected FileContentChangeChecker getKeyConfChangeChecker() throws Exception {
                return new FileContentChangeChecker("dummyFileName") {
                    @Override
                    protected String calculateConfFileChecksum(File file) throws Exception {
                        return "dummyChecksum";
                    }
                    @Override
                    public boolean hasChanged() throws Exception {
                        return true;
                    }
                };
            }

            @Override
            protected CachedAuthKeyInfoImpl getAuthKeyInfo() throws Exception {
                callsToGetAuthKeyInfo.incrementAndGet();
                allThreadsAreWaitingForKey.countDown();
                return new CachedAuthKeyInfoImpl(null, null, null);
            }
        };

        Callable<AuthKey> callableTask = () -> {
            return testCachingKeyConf.getAuthKey();
        };

        List<Callable<AuthKey>> fiveCallables = Arrays.asList(new Callable[]{
                callableTask, callableTask, callableTask, callableTask, callableTask});
        List<Future<AuthKey>> results = executorService.invokeAll(fiveCallables);

        allThreadsAreWaitingForKey.await();

        for (Future<AuthKey> result: results) {
            result.get();
        }
        // should have read the key just once
        assertEquals(1, callsToGetAuthKeyInfo.get());
    }

}
