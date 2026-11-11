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
package org.niis.xroad.proxy.controlplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RefusalMemoryTest {

    private static final AssetAccessCache.CacheKey KEY = new AssetAccessCache.CacheKey(
            "DEV:COM:222",
            ServiceId.Conf.create("DEV", "COM", "222", "TESTSERVICE", "petstore").asEncodedId(),
            Set.of(new AcquisitionCandidate(
                    "did:web:ss0.example.org%3A7183:v1:DEV:COM:222",
                    "https://ss0.example.org:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1")),
            null,
            "http-dsp-profile-2025-1",
            ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT").asEncodedId());

    private final AtomicLong nanos = new AtomicLong();
    private final RefusalMemory memory = new RefusalMemory(nanos::get);

    @ParameterizedTest
    @ValueSource(strings = {"dsp_access_refused", "dsp_dataset_not_found", "dsp_offers_not_found"})
    void aNegotiationRefusalIsReplayedWithItsCodeUntilItsLifetimeEnds(String refusalCode) {
        memory.rememberIfRefusal(KEY, failure("proxy.dataspace." + refusalCode));

        advance(RefusalMemory.LIFETIME.minusMillis(1));
        assertThat(memory.recall(KEY)).get()
                .extracting(XrdRuntimeException::getCode)
                .isEqualTo("proxy.dataspace." + refusalCode);

        advance(Duration.ofMillis(1));
        assertThat(memory.recall(KEY)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dsp_transfer_failed", "dsp_acquisition_failed", "dsp_acquisition_timeout",
            "dsp_negotiation_failed", "dsp_catalog_fetch_failed"})
    void aTransferOrTransportOrOtherFailureIsNotRemembered(String faultCode) {
        memory.rememberIfRefusal(KEY, failure("proxy.dataspace." + faultCode));

        assertThat(memory.recall(KEY)).isEmpty();
    }

    @Test
    void aFailureThatIsNoXroadErrorIsNotRemembered() {
        memory.rememberIfRefusal(KEY, new IllegalStateException("channel closed"));

        assertThat(memory.recall(KEY)).isEmpty();
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    private static XrdRuntimeException failure(String code) {
        return XrdRuntimeException.systemException(ErrorCode.withCode(code)).build();
    }
}
