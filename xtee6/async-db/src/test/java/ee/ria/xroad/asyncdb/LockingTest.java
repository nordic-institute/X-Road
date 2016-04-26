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
package ee.ria.xroad.asyncdb;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To see exactly when the locks are acquired and released, run the main method
 * simultaneously in two separate processes.
 */
public final class LockingTest {
    private LockingTest() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(
            LockingTest.class);

    /**
     * Invokes locking
     * @param args - main method parameters, not used.
     * @throws Exception - when invocation of locking fails.
     */
    public static void main(String[] args) throws Exception {
        LOG.debug("Starting up");

        Callable<Object> task = () -> {
            LOG.debug("Got lock");

            // FileUtils.writeStringToFile(
            // new File(getFilePath()),
            // "hello, world!\n" + this);

            Thread.sleep(20000);

            LOG.debug("Releasing lock");
            return null;
        };

        AsyncDBUtil.performLocked(task, "/tmp/locktest", new Object());
        LOG.debug("Shutting down");
    }
}
