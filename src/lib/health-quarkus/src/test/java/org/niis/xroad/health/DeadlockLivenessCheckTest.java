/*
 * The MIT License
 *
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
package org.niis.xroad.health;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeadlockLivenessCheckTest {

    private final DeadlockLivenessCheck check = new DeadlockLivenessCheck();

    @Test
    void shouldReturnUpWhenNoDeadlock() {
        HealthCheckResponse response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("DEADLOCK_CHECK", response.getName());
    }

    @Test
    void shouldReturnDownWhenDeadlockExists() throws InterruptedException {
        final Object lock1 = new Object();
        final Object lock2 = new Object();
        CountDownLatch bothLocked = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            synchronized (lock1) {
                bothLocked.countDown();
                try {
                    bothLocked.await(5, TimeUnit.SECONDS);
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    return;
                }
                synchronized (lock2) {
                    Thread.onSpinWait(); // never reached — deadlocked
                }
            }
        }, "deadlock-test-thread-1");

        Thread t2 = new Thread(() -> {
            synchronized (lock2) {
                bothLocked.countDown();
                try {
                    bothLocked.await(5, TimeUnit.SECONDS);
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                    return;
                }
                synchronized (lock1) {
                    Thread.onSpinWait(); // never reached — deadlocked
                }
            }
        }, "deadlock-test-thread-2");

        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        // Wait for both threads to acquire their first locks and then attempt the second
        bothLocked.await(5, TimeUnit.SECONDS);
        Thread.sleep(500); // allow time for deadlock to form

        try {
            HealthCheckResponse response = check.call();

            assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
            assertEquals("DEADLOCK_CHECK", response.getName());
        } finally {
            t1.interrupt();
            t2.interrupt();
        }
    }
}
