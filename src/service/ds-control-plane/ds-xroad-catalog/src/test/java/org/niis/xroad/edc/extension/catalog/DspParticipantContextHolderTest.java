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
package org.niis.xroad.edc.extension.catalog;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DspParticipantContextHolderTest {

    @Test
    void getReturnsNullWhenNothingSet() {
        var holder = new DspParticipantContextHolder();

        assertThat(holder.get()).isNull();
    }

    @Test
    void getReturnsWhateverWasSet() {
        var holder = new DspParticipantContextHolder();

        holder.set("DEV:COM:5678");

        assertThat(holder.get()).isEqualTo("DEV:COM:5678");
    }

    @Test
    void clearRemovesTheValue() {
        var holder = new DspParticipantContextHolder();
        holder.set("DEV:COM:5678");

        holder.clear();

        assertThat(holder.get()).isNull();
    }

    @Test
    void valueIsIsolatedPerThread() throws InterruptedException {
        var holder = new DspParticipantContextHolder();
        holder.set("host-thread-context");

        var otherThreadValue = new AtomicReference<String>();
        var latch = new CountDownLatch(1);
        var otherThread = new Thread(() -> {
            otherThreadValue.set(holder.get());
            latch.countDown();
        });
        otherThread.start();
        latch.await();

        assertThat(otherThreadValue.get()).isNull();
        assertThat(holder.get()).isEqualTo("host-thread-context");
    }
}
